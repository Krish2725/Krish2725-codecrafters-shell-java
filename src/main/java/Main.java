import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    static class Job {
        int number;
        Process process;
        String command;

        Job(int number, Process process, String command) {
            this.number = number;
            this.process = process;
            this.command = command;
        }
    }

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

    private static void printOutput(String output, String stdoutTarget, boolean append, Path currentDirectory) throws Exception {
        if (stdoutTarget != null) {
            Path targetPath = currentDirectory.resolve(stdoutTarget).normalize();
            
            if (append) {
                Files.writeString(targetPath, output + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(targetPath, output + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } else {
            System.out.println(output);
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner in = new Scanner(System.in);
        Path currentDirectory = Paths.get(System.getProperty("user.dir"));
        int jobNumber = 1;
        ArrayList<Job> jobsList = new ArrayList<>();

        while (true) {
            System.out.print("$ ");
            String command = in.nextLine();
            
            if (command.trim().isEmpty()) {
                continue;
            }

            ArrayList<String> parts = parseCommand(command);
            
            if (parts.isEmpty()) continue;

            boolean runInBackground = false;
            if (parts.get(parts.size() - 1).equals("&")) {
                runInBackground = true;
                parts.remove(parts.size() - 1);
            }

            if (parts.isEmpty()) continue;

            String stdoutTarget = null;
            String stderrTarget = null;
            boolean appendStdout = false;
            boolean appendStderr = false;
            
            for (int i = parts.size() - 2; i >= 0; i--) {
                String token = parts.get(i);
                if (token.equals(">") || token.equals("1>")) {
                    stdoutTarget = parts.get(i + 1);
                    appendStdout = false;
                    parts.remove(i + 1);
                    parts.remove(i);
                } else if (token.equals(">>") || token.equals("1>>")) {
                    stdoutTarget = parts.get(i + 1);
                    appendStdout = true;
                    parts.remove(i + 1);
                    parts.remove(i);
                } else if (token.equals("2>")) {
                    stderrTarget = parts.get(i + 1);
                    appendStderr = false;
                    parts.remove(i + 1);
                    parts.remove(i);
                } else if (token.equals("2>>")) {
                    stderrTarget = parts.get(i + 1);
                    appendStderr = true;
                    parts.remove(i + 1);
                    parts.remove(i);
                }
            }

            if (parts.isEmpty()) continue;

            if (stdoutTarget != null) {
                Path outPath = currentDirectory.resolve(stdoutTarget).normalize();
                if (outPath.getParent() != null && !Files.exists(outPath.getParent())) {
                    Files.createDirectories(outPath.getParent());
                }
                Files.writeString(outPath, "", StandardOpenOption.CREATE, appendStdout ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            }
            if (stderrTarget != null) {
                Path errPath = currentDirectory.resolve(stderrTarget).normalize();
                if (errPath.getParent() != null && !Files.exists(errPath.getParent())) {
                    Files.createDirectories(errPath.getParent());
                }
                Files.writeString(errPath, "", StandardOpenOption.CREATE, appendStderr ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            }

            String cmd = parts.get(0);

            if (cmd.equals("exit")) {
                System.exit(0);
            } 
            else if (cmd.equals("pwd")) {
                printOutput(currentDirectory.toString(), stdoutTarget, appendStdout, currentDirectory);
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
                    String errorMsg = "cd: " + dir + ": No such file or directory";
                    if (stderrTarget != null) {
                        Path errPath = currentDirectory.resolve(stderrTarget).normalize();
                        Files.writeString(errPath, errorMsg + "\n", StandardOpenOption.CREATE, appendStderr ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
                    } else {
                        System.out.println(errorMsg);
                    }
                }
            } 
            else if (cmd.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(parts.get(i));
                }
                printOutput(sb.toString(), stdoutTarget, appendStdout, currentDirectory);
            } 
            else if (cmd.equals("jobs")) {
                StringBuilder sb = new StringBuilder();
                for (Job job : jobsList) {
                    if (job.process.isAlive()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append("[").append(job.number).append("]+ Running                 ").append(job.command);
                    }
                }
                if (sb.length() > 0) {
                    printOutput(sb.toString(), stdoutTarget, appendStdout, currentDirectory);
                }
            }
            else if (cmd.equals("type")) {
                if (parts.size() < 2) continue;
                String target = parts.get(1);

                if (target.equals("exit") || target.equals("pwd") || target.equals("echo") || target.equals("type") || target.equals("cd") || target.equals("jobs")) {
                    printOutput(target + " is a shell builtin", stdoutTarget, appendStdout, currentDirectory);
                } else {
                    String[] paths = System.getenv("PATH").split(":");
                    boolean found = false;

                    for (String path : paths) {
                        Path fullPath = Paths.get(path, target);
                        if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                            printOutput(target + " is " + fullPath, stdoutTarget, appendStdout, currentDirectory);
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        printOutput(target + ": not found", stdoutTarget, appendStdout, currentDirectory);
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
                        
                        pb.inheritIO();
                        
                        if (stdoutTarget != null) {
                            File outFile = currentDirectory.resolve(stdoutTarget).normalize().toFile();
                            if (appendStdout) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                            } else {
                                pb.redirectOutput(outFile);
                            }
                        }
                        
                        if (stderrTarget != null) {
                            File errFile = currentDirectory.resolve(stderrTarget).normalize().toFile();
                            if (appendStderr) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                            } else {
                                pb.redirectError(errFile);
                            }
                        }

                        Process p = pb.start();
                        
                        if (runInBackground) {
                            System.out.println("[" + jobNumber + "] " + p.pid());
                            jobsList.add(new Job(jobNumber, p, command));
                            jobNumber++;
                        } else {
                            p.waitFor();
                        }

                        found = true;
                        break;
                    }
                }

                if (!found) {
                    String errorMsg = executable + ": command not found\n";
                    
                    if (stderrTarget != null) {
                        Path errPath = currentDirectory.resolve(stderrTarget).normalize();
                        Files.writeString(errPath, errorMsg, StandardOpenOption.CREATE, appendStderr ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
                    } else {
                        System.out.print(errorMsg);
                    }
                }
            }
        }
    }
}