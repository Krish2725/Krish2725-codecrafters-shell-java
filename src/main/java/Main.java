import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("exit") || cmd.equals("pwd") || cmd.equals("cd") || 
               cmd.equals("echo") || cmd.equals("type") || cmd.equals("jobs");
    }

    private static String getBuiltinOutput(List<String> parts, Path currentDirectory, ArrayList<Job> jobsList) {
        String cmd = parts.get(0);
        if (cmd.equals("pwd")) return currentDirectory.toString();
        if (cmd.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.size(); i++) {
                if (i > 1) sb.append(" ");
                sb.append(parts.get(i));
            }
            return sb.toString();
        }
        if (cmd.equals("type")) {
            if (parts.size() < 2) return "";
            String target = parts.get(1);
            if (isBuiltin(target)) return target + " is a shell builtin";
            String[] paths = System.getenv("PATH").split(":");
            for (String p : paths) {
                Path fullPath = Paths.get(p, target);
                if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                    return target + " is " + fullPath.toString();
                }
            }
            return target + ": not found";
        }
        if (cmd.equals("jobs")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < jobsList.size(); i++) {
                Job job = jobsList.get(i);
                char marker = ' ';
                if (i == jobsList.size() - 1) marker = '+';
                else if (i == jobsList.size() - 2) marker = '-';
                if (sb.length() > 0) sb.append("\n");
                if (job.process.isAlive()) {
                    sb.append("[").append(job.number).append("]").append(marker).append(" Running                 ").append(job.command);
                } else {
                    String doneCmd = job.command.substring(0, job.command.lastIndexOf('&')).trim();
                    sb.append("[").append(job.number).append("]").append(marker).append(" Done                    ").append(doneCmd);
                }
            }
            return sb.toString();
        }
        return "";
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

    private static void printOutput(String output, boolean addNewline, String stdoutTarget, boolean appendStdout, String stderrTarget, boolean appendStderr, Path currentDirectory) throws Exception {
        if (stdoutTarget != null) {
            Path targetPath = currentDirectory.resolve(stdoutTarget).normalize();
            if (targetPath.getParent() != null && !Files.exists(targetPath.getParent())) {
                Files.createDirectories(targetPath.getParent());
            }
            String toWrite = addNewline ? output + "\n" : output;
            Files.writeString(targetPath, toWrite, StandardOpenOption.CREATE, appendStdout ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            if (addNewline) System.out.println(output);
            else System.out.print(output);
        }

        if (stderrTarget != null) {
            Path errPath = currentDirectory.resolve(stderrTarget).normalize();
            if (errPath.getParent() != null && !Files.exists(errPath.getParent())) {
                Files.createDirectories(errPath.getParent());
            }
            if (!appendStderr) {
                Files.writeString(errPath, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else if (!Files.exists(errPath)) {
                Files.writeString(errPath, "", StandardOpenOption.CREATE);
            }
        }
    }

    private static void printError(String errorMsg, boolean addNewline, String stdoutTarget, boolean appendStdout, String stderrTarget, boolean appendStderr, Path currentDirectory) throws Exception {
        if (stderrTarget != null) {
            Path errPath = currentDirectory.resolve(stderrTarget).normalize();
            if (errPath.getParent() != null && !Files.exists(errPath.getParent())) {
                Files.createDirectories(errPath.getParent());
            }
            String toWrite = addNewline ? errorMsg + "\n" : errorMsg;
            Files.writeString(errPath, toWrite, StandardOpenOption.CREATE, appendStderr ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            if (addNewline) System.out.println(errorMsg);
            else System.out.print(errorMsg);
        }

        if (stdoutTarget != null) {
            Path outPath = currentDirectory.resolve(stdoutTarget).normalize();
            if (outPath.getParent() != null && !Files.exists(outPath.getParent())) {
                Files.createDirectories(outPath.getParent());
            }
            if (!appendStdout) {
                Files.writeString(outPath, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else if (!Files.exists(outPath)) {
                Files.writeString(outPath, "", StandardOpenOption.CREATE);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner in = new Scanner(System.in);
        Path currentDirectory = Paths.get(System.getProperty("user.dir"));
        int jobNumber = 1;
        ArrayList<Job> jobsList = new ArrayList<>();

        while (true) {
            ArrayList<Job> reapedJobs = new ArrayList<>();
            for (int i = 0; i < jobsList.size(); i++) {
                Job job = jobsList.get(i);
                if (!job.process.isAlive()) {
                    char marker = ' ';
                    if (i == jobsList.size() - 1) {
                        marker = '+';
                    } else if (i == jobsList.size() - 2) {
                        marker = '-';
                    }
                    String doneCmd = job.command.substring(0, job.command.lastIndexOf('&')).trim();
                    System.out.println("[" + job.number + "]" + marker + " Done                    " + doneCmd);
                    reapedJobs.add(job);
                }
            }
            jobsList.removeAll(reapedJobs);

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

            if (parts.isEmpty()) {
                printOutput("", false, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                continue;
            }

            int pipeIndex = -1;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).equals("|")) {
                    pipeIndex = i;
                    break;
                }
            }

            if (pipeIndex != -1) {
                List<String> leftParts = new ArrayList<>(parts.subList(0, pipeIndex));
                List<String> rightParts = new ArrayList<>(parts.subList(pipeIndex + 1, parts.size()));

                String executableLeft = leftParts.get(0);
                String executableRight = rightParts.get(0);
                String[] paths = System.getenv("PATH").split(":");
                
                boolean leftIsBuiltin = isBuiltin(executableLeft);
                boolean rightIsBuiltin = isBuiltin(executableRight);
                
                boolean foundLeft = leftIsBuiltin;
                boolean foundRight = rightIsBuiltin;

                if (!leftIsBuiltin) {
                    for (String p : paths) {
                        if (!foundLeft && Files.exists(Paths.get(p, executableLeft)) && Files.isExecutable(Paths.get(p, executableLeft))) foundLeft = true;
                    }
                }
                
                if (!rightIsBuiltin) {
                    for (String p : paths) {
                        if (!foundRight && Files.exists(Paths.get(p, executableRight)) && Files.isExecutable(Paths.get(p, executableRight))) foundRight = true;
                    }
                }

                if (!foundLeft) {
                    String errorMsg = executableLeft + ": command not found";
                    printError(errorMsg, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                    continue;
                }
                if (!foundRight) {
                    String errorMsg = executableRight + ": command not found";
                    printError(errorMsg, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                    continue;
                }

                if (!leftIsBuiltin && !rightIsBuiltin) {
                    ProcessBuilder pb1 = new ProcessBuilder(leftParts);
                    pb1.directory(currentDirectory.toFile());
                    pb1.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    pb1.redirectError(ProcessBuilder.Redirect.INHERIT);

                    ProcessBuilder pb2 = new ProcessBuilder(rightParts);
                    pb2.directory(currentDirectory.toFile());

                    if (stdoutTarget != null) {
                        File outFile = currentDirectory.resolve(stdoutTarget).normalize().toFile();
                        if (appendStdout) {
                            pb2.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                        } else {
                            pb2.redirectOutput(outFile);
                        }
                    } else {
                        pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (stderrTarget != null) {
                        File errFile = currentDirectory.resolve(stderrTarget).normalize().toFile();
                        if (appendStderr) {
                            pb2.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                        } else {
                            pb2.redirectError(errFile);
                        }
                    } else {
                        pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    List<ProcessBuilder> pbs = new ArrayList<>();
                    pbs.add(pb1);
                    pbs.add(pb2);

                    List<Process> processes = ProcessBuilder.startPipeline(pbs);

                    if (runInBackground) {
                        if (jobsList.isEmpty()) {
                            jobNumber = 1;
                        } else {
                            int maxJobNumber = 0;
                            for (Job j : jobsList) {
                                if (j.number > maxJobNumber) maxJobNumber = j.number;
                            }
                            jobNumber = maxJobNumber + 1;
                        }
                        Process lastP = processes.get(processes.size() - 1);
                        System.out.println("[" + jobNumber + "] " + lastP.pid());
                        jobsList.add(new Job(jobNumber, lastP, command));
                    } else {
                        Process lastP = processes.get(processes.size() - 1);
                        lastP.waitFor();
                    }
                } else if (leftIsBuiltin && !rightIsBuiltin) {
                    String leftOutput = getBuiltinOutput(leftParts, currentDirectory, jobsList);
                    ProcessBuilder pb2 = new ProcessBuilder(rightParts);
                    pb2.directory(currentDirectory.toFile());

                    if (stdoutTarget != null) {
                        File outFile = currentDirectory.resolve(stdoutTarget).normalize().toFile();
                        if (appendStdout) {
                            pb2.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                        } else {
                            pb2.redirectOutput(outFile);
                        }
                    } else {
                        pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (stderrTarget != null) {
                        File errFile = currentDirectory.resolve(stderrTarget).normalize().toFile();
                        if (appendStderr) {
                            pb2.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                        } else {
                            pb2.redirectError(errFile);
                        }
                    } else {
                        pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process p2 = pb2.start();
                    if (!executableLeft.equals("cd") && !executableLeft.equals("exit")) {
                        p2.getOutputStream().write((leftOutput + "\n").getBytes());
                    }
                    p2.getOutputStream().close();

                    if (runInBackground) {
                        if (jobsList.isEmpty()) {
                            jobNumber = 1;
                        } else {
                            int maxJobNumber = 0;
                            for (Job j : jobsList) {
                                if (j.number > maxJobNumber) maxJobNumber = j.number;
                            }
                            jobNumber = maxJobNumber + 1;
                        }
                        System.out.println("[" + jobNumber + "] " + p2.pid());
                        jobsList.add(new Job(jobNumber, p2, command));
                    } else {
                        p2.waitFor();
                    }
                } else if (!leftIsBuiltin && rightIsBuiltin) {
                    ProcessBuilder pb1 = new ProcessBuilder(leftParts);
                    pb1.directory(currentDirectory.toFile());
                    pb1.redirectOutput(new File("/dev/null"));
                    Process p1 = pb1.start();
                    p1.waitFor();

                    String rightOutput = getBuiltinOutput(rightParts, currentDirectory, jobsList);
                    if (executableRight.equals("cd") || executableRight.equals("exit")) {
                        if (executableRight.equals("exit")) System.exit(0);
                        printOutput("", false, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                    } else {
                        printOutput(rightOutput, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                    }
                } else {
                    String rightOutput = getBuiltinOutput(rightParts, currentDirectory, jobsList);
                    if (executableRight.equals("cd") || executableRight.equals("exit")) {
                        if (executableRight.equals("exit")) System.exit(0);
                        printOutput("", false, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                    } else {
                        printOutput(rightOutput, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                    }
                }
                continue;
            }

            String cmd = parts.get(0);

            if (cmd.equals("exit")) {
                System.exit(0);
            } 
            else if (cmd.equals("pwd")) {
                printOutput(currentDirectory.toString(), true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
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
                    printError(errorMsg, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                }
            } 
            else if (cmd.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(parts.get(i));
                }
                printOutput(sb.toString(), true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
            } 
            else if (cmd.equals("jobs")) {
                if (!jobsList.isEmpty()) {
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                }
                StringBuilder sb = new StringBuilder();
                ArrayList<Job> toRemove = new ArrayList<>();
                for (int i = 0; i < jobsList.size(); i++) {
                    Job job = jobsList.get(i);
                    char marker = ' ';
                    if (i == jobsList.size() - 1) {
                        marker = '+';
                    } else if (i == jobsList.size() - 2) {
                        marker = '-';
                    }
                    if (sb.length() > 0) sb.append("\n");
                    if (job.process.isAlive()) {
                        sb.append("[").append(job.number).append("]").append(marker).append(" Running                 ").append(job.command);
                    } else {
                        String doneCmd = job.command.substring(0, job.command.lastIndexOf('&')).trim();
                        sb.append("[").append(job.number).append("]").append(marker).append(" Done                    ").append(doneCmd);
                        toRemove.add(job);
                    }
                }
                if (sb.length() > 0) {
                    printOutput(sb.toString(), true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                } else {
                    printOutput("", false, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                }
                jobsList.removeAll(toRemove);
            }
            else if (cmd.equals("type")) {
                if (parts.size() < 2) continue;
                String target = parts.get(1);

                if (target.equals("exit") || target.equals("pwd") || target.equals("echo") || target.equals("type") || target.equals("cd") || target.equals("jobs")) {
                    printOutput(target + " is a shell builtin", true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                } else {
                    String[] paths = System.getenv("PATH").split(":");
                    boolean found = false;

                    for (String path : paths) {
                        Path fullPath = Paths.get(path, target);
                        if (Files.exists(fullPath) && Files.isExecutable(fullPath)) {
                            printOutput(target + " is " + fullPath, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        printOutput(target + ": not found", true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
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
                            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                            if (appendStdout) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                            } else {
                                pb.redirectOutput(outFile);
                            }
                        }
                        
                        if (stderrTarget != null) {
                            File errFile = currentDirectory.resolve(stderrTarget).normalize().toFile();
                            if (errFile.getParentFile() != null) errFile.getParentFile().mkdirs();
                            if (appendStderr) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                            } else {
                                pb.redirectError(errFile);
                            }
                        }

                        Process p = pb.start();
                        
                        if (runInBackground) {
                            if (jobsList.isEmpty()) {
                                jobNumber = 1;
                            } else {
                                int maxJobNumber = 0;
                                for (Job j : jobsList) {
                                    if (j.number > maxJobNumber) {
                                        maxJobNumber = j.number;
                                    }
                                }
                                jobNumber = maxJobNumber + 1;
                            }
                            System.out.println("[" + jobNumber + "] " + p.pid());
                            jobsList.add(new Job(jobNumber, p, command));
                        } else {
                            p.waitFor();
                        }

                        found = true;
                        break;
                    }
                }

                if (!found) {
                    String errorMsg = executable + ": command not found";
                    printError(errorMsg, true, stdoutTarget, appendStdout, stderrTarget, appendStderr, currentDirectory);
                }
            }
        }
    }
}