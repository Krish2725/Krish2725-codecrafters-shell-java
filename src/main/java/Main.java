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

    for(int i = 0; i < command.length(); i++) {

        char c = command.charAt(i);

        if(c == '\'' && !inDoubleQuotes) {

            inSingleQuotes = !inSingleQuotes;

        }
        else if(c == '"' && !inSingleQuotes) {

            inDoubleQuotes = !inDoubleQuotes;

        }
        else if(c == ' ' &&
                !inSingleQuotes &&
                !inDoubleQuotes) {

            if(current.length() > 0) {
                parts.add(current.toString());
                current.setLength(0);
            }

        }
        else {

            current.append(c);
        }
    }

    if(current.length() > 0) {
        parts.add(current.toString());
    }

    return parts;
}

    public static void main(String[] args) throws Exception {

        int k = 100;
        Scanner in = new Scanner(System.in);

        Path currentDirectory = Paths.get(System.getProperty("user.dir"));

        while (k == 100) {

            System.out.print("$ ");
            String command = in.nextLine();

            if (command.startsWith("type")) {

                String target = command.substring(5);

                if (target.equals("exit")) {
                    System.out.println(target + " is a shell builtin");
                }
                else if (target.equals("pwd")) {
                    System.out.println(target + " is a shell builtin");
                }
                else if (target.equals("echo")) {
                    System.out.println(target + " is a shell builtin");
                }
                else if (target.equals("type")) {
                    System.out.println(target + " is a shell builtin");
                }
                else if (target.equals("cd")) {
                    System.out.println(target + " is a shell builtin");
                }
                else {

                    String[] paths = System.getenv("PATH").split(":");

                    boolean found = false;

                    for (String path : paths) {

                        Path fullPath = Paths.get(path, target);

                        if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                            System.out.println(target + " is " + fullPath);
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.out.println(target + ": not found");
                    }
                }
            }
            else {

                if (command.equals("exit")) {
                    System.exit(0);
                }

                else if (command.equals("pwd")) {
                    System.out.println(currentDirectory.toString());
                }

                else if (command.startsWith("cd ")) {

                    String dir = command.substring(3);

                    Path targetPath;

                    if (dir.equals("~")) {

                        targetPath = Paths.get(System.getenv("HOME"));

                    }
                    else if (Paths.get(dir).isAbsolute()) {

                        targetPath = Paths.get(dir);

                    }
                    else {

                        targetPath = currentDirectory.resolve(dir);
                    }

                    targetPath = targetPath.normalize();

                    if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                        currentDirectory = targetPath;
                    }
                    else {
                        System.out.println(
                                "cd: " + dir + ": No such file or directory"
                        );
                    }
                }

                else if (command.startsWith("echo ")) {

                    ArrayList<String> parts = parseCommand(command);

                    for (int i = 1; i < parts.size(); i++) {

                        if (i > 1) {
                            System.out.print(" ");
                        }

                        System.out.print(parts.get(i));
                    }

                    System.out.println();
                }

                else if (command.startsWith("type")) {
                    System.out.print(command.substring(5) + ": not found");
                    System.out.println();
                }

                else {

                    ArrayList<String> parts = parseCommand(command);

                    String executable = parts.get(0);

                    String[] paths = System.getenv("PATH").split(":");

                    boolean found = false;

                    for (String path : paths) {

                        Path fullPath = Paths.get(path, executable);

                        if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {

                            ArrayList<String> cmd = new ArrayList<>();

                            cmd.add(executable);

                            for (int i = 1; i < parts.size(); i++) {
                                cmd.add(parts.get(i));
                            }

                            ProcessBuilder pb = new ProcessBuilder(cmd);

                            pb.directory(currentDirectory.toFile());
                            pb.inheritIO();

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
}