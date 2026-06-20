import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    private static ArrayList<String> parseCommand(String command) {

        ArrayList<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '\\' && inDoubleQuotes) {
                if (i + 1 < command.length()) {
                    char next = command.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append('\\');
                    }
                } else {
                    current.append('\\');
                }
            }
            else if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < command.length()) {
                    current.append(command.charAt(i + 1));
                    i++;
                }
            }
            else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }
            else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            }
            else if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            }
            else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    private static void printOutput(String output, String redirectTarget, Path currentDirectory) throws Exception {
        if (redirectTarget != null) {
            Path targetPath = currentDirectory.resolve(redirectTarget).normalize();
            Files.writeString(targetPath, output + "\n");
        } else {
            System.out.println(output);
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner in = new Scanner(System.in);
        Path currentDirectory = Paths.get(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            String command = in.nextLine();
            
            if (command.trim().isEmpty()) {
                continue;
            }

            ArrayList<String> parts = parseCommand(command);

            String redirectTarget = null;
            for (int i = parts.size() - 2; i >= 0; i--) {
                if (parts.get(i).equals(">") || parts.get(i).equals("1>")) {
                    redirectTarget = parts.get(i + 1);
                    
                    parts.remove(i);
                    parts.remove(i);
                    break;
                }
            }

            if (parts.isEmpty()) continue;

            String cmd = parts.get(0);

            if (cmd.equals("exit")) {
                System.exit(0);
            } 
            else if (cmd.equals("pwd")) {
                printOutput(currentDirectory.toString(), redirectTarget, currentDirectory);
            } 
            else if (cmd.equals("cd")) {
                String dir = parts.size() > 1 ? parts.get(1) : "~";
                Path targetPath;

                if (dir.equals("~")) {
                    targetPath = Paths.get(System.getenv("HOME"));
                } else if (Paths.get(dir).isAbsolute()) {
                    targetPath = Paths.get(dir);
                } else {
                    targetPath = currentDirectory.resolve(dir);
                }

                targetPath = targetPath.normalize();

                if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                    currentDirectory = targetPath;
                } else {
                    System.out.println("cd: " + dir + ": No such file or directory");
                }
            } 
            else if (cmd.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(parts.get(i));
                }
                printOutput(sb.toString(), redirectTarget, currentDirectory);
            } 
            else if (cmd.equals("type")) {
                if (parts.size() < 2) continue;
                String target = parts.get(1);

                if (target.equals("exit") || target.equals("pwd") || target.equals("echo") || target.equals("type") || target.equals("cd")) {
                    printOutput(target + " is a shell builtin", redirectTarget, currentDirectory);
                } else {
                    String[] paths = System.getenv("PATH").split(":");
                    boolean found = false;

                    for (String path : paths) {
                        Path fullPath = Paths.get(path, target);
                        if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                            printOutput(target + " is " + fullPath, redirectTarget, currentDirectory);
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        printOutput(target + ": not found", redirectTarget, currentDirectory);
                    }
                }
            } 
            else {
                String executable = parts.get(0);
                String[] paths = System.getenv("PATH").split(":");
                boolean found = false;

                for (String path : paths) {
                    Path fullPath = Paths.get(path, executable);

                    if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(currentDirectory.toFile());
                        
                        // Fix: Always inherit IO first (handles stderr and stdin perfectly)
                        pb.inheritIO();
                        
                        // Then, if redirecting, overwrite just the stdout property
                        if (redirectTarget != null) {
                            File outFile = currentDirectory.resolve(redirectTarget).normalize().toFile();
                            pb.redirectOutput(outFile);
                        }

                        Process p = pb.start();
                        p.waitFor();

                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(executable + ": command not found");
                }
            }
        }
    }
}