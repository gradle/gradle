/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.fixtures.timeout;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * NOTICE: This class is used in LifeCyclePlugin so you must:
 * 1. NOT DEPENDS ON ANY 3RD-PARTY LIBRARIES except JDK 11.
 * 2. UPDATE build-logic/lifecycle/src/main/kotlin/PrintStackTracesOnTimeoutBuildService.kt if this class is moved to another package.
 *
 * Used to print all JVMs' thread dumps on the machine. When it starts working, the process/machine might be in a bad state (e.g. deadlock),
 * So we don't want it to depend on any third-party libraries. It's executed directly via `java JavaProcessStackTracesMonitor` in `PrintStackTracesOnTimeoutBuildService`.
 *
 * To avoid leaking credentials, it prints everything to a file (in current working directory if not specified).
 *
 * This class is executed both in gradle/gradle build and integration test (see {@link IntegrationTestTimeout}.
 */
public class JavaProcessStackTracesMonitor {
    private static final Pattern UNIX_JAVA_COMMAND_PATTERN = Pattern.compile("(?i)([^\\s]+/bin/java)");
    private static final Pattern WINDOWS_JAVA_COMMAND_PATTERN = Pattern.compile("(?i)(?m)^\"?(.*[/\\\\]bin[/\\\\]java\\.exe)");
    private static final Pattern WINDOWS_PID_PATTERN = Pattern.compile("([0-9]+)\\s*$");
    private static final Pattern UNIX_PID_PATTERN = Pattern.compile("([0-9]+)");

    private final File outputFile;
    private final PrintStream output;

    public JavaProcessStackTracesMonitor(File dumpDir) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
            this.outputFile = new File(dumpDir, timestamp + ".threaddump");
            this.output = new PrintStream(outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new JavaProcessStackTracesMonitor(new File(args.length == 0 ? "." : args[0])).printAllStackTracesByJstack();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static class JavaProcessInfo {
        String pid;
        String javaCommand;

        public JavaProcessInfo(String pid, String javaCommand) {
            this.pid = pid;
            this.javaCommand = javaCommand;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            JavaProcessInfo that = (JavaProcessInfo) o;
            return Objects.equals(pid, that.pid) &&
                Objects.equals(javaCommand, that.javaCommand);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pid, javaCommand);
        }

        @Override
        public String toString() {
            return "JavaProcessInfo{" +
                "pid='" + pid + '\'' +
                ", javaCommand='" + javaCommand + '\'' +
                '}';
        }

        String getJstackCommand() {
            assertTrue(javaCommand.endsWith("java") || javaCommand.endsWith("java.exe"), "Unknown java command：" + javaCommand);

            Path javaPath = Paths.get(javaCommand);
            if (javaPath.getParent().getFileName().toString().equals("bin") && javaPath.getParent().getParent().getFileName().toString().equals("jre")) {
                return javaPath.resolve("../../../bin/jstack").normalize().toString();
            } else {
                return javaPath.resolve("../../bin/jstack").normalize().toString();
            }
        }

        String jstack() {
            try {
                ExecResult result = run(getJstackCommand(), pid);

                StringBuilder sb = new StringBuilder(String.format("Run %s %s return %s", getJstackCommand(), pid, result));
                if (result.code != 0) {
                    result = run(getJstackCommand(), "-F", pid);
                    sb.append(String.format("Run %s -F %s return %s", getJstackCommand(), pid, result.toString()));
                }
                return sb.toString();
            } catch (Throwable e) {
                // e.g. java.lang.IllegalThreadStateException: process has not exited
                //          at java.base/java.lang.ProcessImpl.exitValue(ProcessImpl.java:553)
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                return sw.toString();
            }
        }
    }

    public static class StdoutAndPatterns {
        String stdout;
        Pattern pidPattern;
        Pattern javaCommandPattern;

        public StdoutAndPatterns(String stdout) {
            this.stdout = stdout;
            if (isWindows()) {
                pidPattern = WINDOWS_PID_PATTERN;
                javaCommandPattern = WINDOWS_JAVA_COMMAND_PATTERN;
            } else {
                pidPattern = UNIX_PID_PATTERN;
                javaCommandPattern = UNIX_JAVA_COMMAND_PATTERN;
            }
        }

        List<JavaProcessInfo> getSuspiciousDaemons() {
            return Stream.of(stdout.split("\\n"))
                .filter(this::isSuspiciousDaemon)
                .map(this::extractProcessInfo)
                .collect(Collectors.toList());
        }

        private JavaProcessInfo extractProcessInfo(String line) {
            Matcher javaCommandMatcher = javaCommandPattern.matcher(line);
            Matcher pidMatcher = pidPattern.matcher(line);

            javaCommandMatcher.find();
            pidMatcher.find();
            return new JavaProcessInfo(pidMatcher.group(1), javaCommandMatcher.group(1));
        }

        private boolean isSuspiciousDaemon(String line) {
            return !isTeamCityAgent(line) && javaCommandPattern.matcher(line).find() && pidPattern.matcher(line).find();
        }

        private boolean isTeamCityAgent(String line) {
            return line.contains("jetbrains.buildServer.agent.AgentMain");
        }
    }

    private StdoutAndPatterns ps() {
        String[] command = isWindows() ? new String[]{"wmic", "process", "get", "processid,commandline"} : new String[]{"ps", "x"};
        ExecResult result = run(command);
        output.printf("Run: %s", Arrays.toString(command));
        output.printf("Stdout: %s", result.stdout);
        output.printf("Stderr: %s", result.stderr);

        result.assertZeroExit();
        return new StdoutAndPatterns(result.stdout);
    }

    private static class ExecResult {
        String[] args;
        int code;
        String stdout;
        String stderr;

        public ExecResult(String[] args, int code, String stdout, String stderr) {
            this.args = args;
            this.code = code;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public String toString() {
            return "ExecResult{" +
                "code=" + code +
                "\n stdout='" + stdout + '\'' +
                "\n stderr='" + stderr + '\'' +
                '}';
        }

        ExecResult assertZeroExit() {
            assertTrue(code == 0, String.format("%s returns %d\n", Arrays.toString(args), code));
            return this;
        }
    }

    private static ExecResult run(String... args) {
        try {
            Process process = new ProcessBuilder().command(args).start();
            CountDownLatch latch = new CountDownLatch(2);
            ByteArrayOutputStream stdout = connectStream(process.getInputStream(), latch);
            ByteArrayOutputStream stderr = connectStream(process.getErrorStream(), latch);

            process.waitFor(1, TimeUnit.MINUTES);
            latch.await(1, TimeUnit.MINUTES);
            return new ExecResult(args, process.exitValue(), stdout.toString(), stderr.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ByteArrayOutputStream connectStream(InputStream forkedProcessOutput, CountDownLatch latch) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os, true);
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(forkedProcessOutput));
                String line;
                while ((line = reader.readLine()) != null) {
                    ps.println(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        }).start();
        return os;
    }


    public File printAllStackTracesByJstack() {
        output.println(ps().getSuspiciousDaemons().stream().map(JavaProcessInfo::jstack).collect(Collectors.joining("\n")));
        return outputFile;
    }
}
