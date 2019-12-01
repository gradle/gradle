/*
 * Copyright 2019 the original author or authors.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Redirect.ALWAYS;

/**
 * TL;DR: Run `java gradle/Check.java` in gradle project root directory with Java 11+.
 *
 * The main goals of this script (yes, thanks to Java 11 scripting engine, it's a "java" script) are:
 *
 * 1. Run a super fast check on changed java files since last push. (git pre-push hook)
 * 2. No dependencies on anything.
 * 3. Extremely simple to use, hopefully no parameters at all.
 *
 * By default, it checks all changed java files since last push (`git diff --name-only {upstreamBranch} HEAD`)
 * plus uncommitted java files. If you want to change the diff target, set the environment variable `GRADLE_DIFF_TARGET`.
 *
 * To see verbose output for troubleshooting, set environment variable `GRADLE_VERBOSE=true`.
 */
public class Check {
    private static final String CHECKSTYLE_VERSION = "8.26";
    private static final String CHECKSTYLE_ALL_JAR_URL = String.format("https://github.com/checkstyle/checkstyle/releases/download/%s-%s/%s-%s-all.jar",
        "checkstyle", CHECKSTYLE_VERSION, "checkstyle", CHECKSTYLE_VERSION);
    private static final File CHECKSTYLE_ALL_JAR_FILE = new File(String.format("build/%s-%s-all.jar", "checkstyle", CHECKSTYLE_VERSION));
    private static final boolean VERBOSE = Boolean.parseBoolean(System.getenv("GRADLE_VERBOSE"));
    private static final boolean NO_CHECK = Boolean.parseBoolean(System.getenv("GRADLE_NO_CHECK"));


    public static void main(String[] args) {
        if (NO_CHECK) {
            System.out.println("No check due to explicit environment variable GRADLE_NO_CHECK=true");
            return;
        }

        assertTrue(new File("build.gradle.kts").isFile(), "This script must be executed in Gradle project root!");

        downloadCheckstyleAllJarIfNotExist();

        Set<String> filesToCheck = new HashSet<>();
        String diffTarget = determineDiffTarget();
        if (diffTarget != null) {
            filesToCheck.addAll(getChangedJavaFiles("git", "diff", "--name-only", diffTarget, "HEAD")); // commited
        }
        filesToCheck.addAll(getChangedJavaFiles("git", "diff", "--name-only", "--staged")); // staged
        filesToCheck.addAll(getChangedJavaFiles("git", "diff", "--name-only")); // unstaged

        if (filesToCheck.isEmpty()) {
            System.out.println("Changed Java files not found, abort.");
            return;
        }

        verboseLog("\nFiles to be checked: \n------\n" + String.join("\n", filesToCheck) + "\n------\n");

        List<String> checkstyleArgs = new ArrayList<>(Arrays.asList(
            "java",
            "-cp", CHECKSTYLE_ALL_JAR_FILE.getAbsolutePath(),
            "-Dconfig_loc=config/checkstyle",
            "com.puppycrawl.tools.checkstyle.Main",
            "-c",
            "config/checkstyle/checkstyle.xml"));
        checkstyleArgs.addAll(filesToCheck);

        assertTrue(run(true, checkstyleArgs.toArray(new String[0])).code == 0, "Checkstyle failed.");
    }

    private static void verboseLog(String message) {
        if (VERBOSE) {
            System.out.println(message);
        }
    }

    // Determine which branch to run git diff against.
    // If upstream branch not set, return null.
    private static String determineDiffTarget() {
        String target = System.getenv("GRADLE_DIFF_TARGET");
        if (target != null) {
            return target;
        }

        // Run git rev-parse --abbrev-ref --symbolic-full-name @{u}
        ExecResult result = run(false, "git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}");

        if (result.code != 0) {
            // When upstream branch not set or in detached HEAD stage
            System.out.println(result.stderr);
            System.out.println("WARNING: upstream branch not found, will only check uncommitted files.");
            return null;
        } else {
            return result.stdout.trim();
        }
    }

    private static Set<String> getChangedJavaFiles(String... gitCommand) {
        String[] lines = run(false, gitCommand).assertZeroExit().stdout.split("\\n");
        return Stream.of(lines).map(String::trim).filter(s -> s.endsWith(".java")).collect(Collectors.toSet());
    }

    private static void downloadCheckstyleAllJarIfNotExist() {
        if (!CHECKSTYLE_ALL_JAR_FILE.isFile()) {
            CHECKSTYLE_ALL_JAR_FILE.getParentFile().mkdirs();

            System.out.println("Downloading from " + CHECKSTYLE_ALL_JAR_URL + " ...");

            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(CHECKSTYLE_ALL_JAR_URL)).build();
                HttpResponse<InputStream> response = HttpClient.newBuilder().followRedirects(ALWAYS).build().send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (BufferedInputStream is = new BufferedInputStream(response.body());
                     BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(CHECKSTYLE_ALL_JAR_FILE))) {
                    int bytesRead = 0;
                    while (true) {
                        int data = is.read();
                        if (data == -1) {
                            break;
                        } else {
                            os.write(data);
                            if (bytesRead++ % (128 * 1024) == 0) {
                                System.out.print(".");
                            }
                        }
                    }
                }
                System.out.println();
            } catch (Exception e) {
                CHECKSTYLE_ALL_JAR_FILE.delete();
                throw new RuntimeException(e);
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static class ExecResult {
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

        ExecResult assertZeroExit() {
            assertTrue(code == 0, String.format("%s return:\n%s\n%s\n", Arrays.toString(args), stdout, stderr));
            return this;
        }
    }

    static ExecResult run(boolean inheritIO, String... args) {
        try {
            verboseLog("-----\nRunning command: " + Stream.of(args).collect(Collectors.joining(" ")));

            Process process = new ProcessBuilder().command(args).start();
            CountDownLatch latch = new CountDownLatch(2);
            ByteArrayOutputStream stdout = connectStream(process.getInputStream(), (inheritIO || VERBOSE) ? System.out : null, latch);
            ByteArrayOutputStream stderr = connectStream(process.getErrorStream(), (inheritIO || VERBOSE) ? System.err : null, latch);

            latch.await();
            verboseLog("-----\n");
            return new ExecResult(args, process.waitFor(), stdout.toString(), stderr.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static ByteArrayOutputStream connectStream(InputStream forkedProcessOutput, PrintStream thisProcessOutput, CountDownLatch latch) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os, true);
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(forkedProcessOutput));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (thisProcessOutput != null) {
                        thisProcessOutput.println(line);
                    }
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
}
