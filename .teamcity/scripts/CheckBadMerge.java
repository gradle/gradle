/*
 * Copyright 2023 the original author or authors.
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

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * See https://github.com/gradle/gradle-private/issues/3919
 *
 * When merging `releaseX` branch into `master`, we should only use the release note from the `master` branch,
 * but sometimes changes on release notes.md was brought to master and merged unnoticed,
 * e.g https://github.com/gradle/gradle/pull/25825
 *
 * This script is to check if there is any merge commit that brings changes from release branch to master.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckBadMerge.java < commits.txt
 *
 * If any "bad" merge commit is found, it will print the details and exit with non-zero code.
 */
public class CheckBadMerge {
    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private static final List<String> MONITORED_PATHS = Collections.unmodifiableList(Arrays.asList(
        "subprojects/docs/src/docs/release/notes.md",
        "platforms/documentation/docs/src/docs/release/notes.md",
        "platforms/documentation/docs/src/docs/release/release-notes-assets/",
        "subprojects/launcher/src/main/resources/release-features.txt",
        "platforms/core-runtime/launcher/src/main/resources/release-features.txt"
    ));

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            System.err.println("Usage: java CheckBadMerge.java < commits.txt");
            System.exit(2);
        }

        List<String> commits = readCommitsFromStdin();

        try {
            for (String commit : commits) {
                checkCommit(commit);
            }
        } finally {
            THREAD_POOL.shutdown();
        }
    }

    private static List<String> readCommitsFromStdin() throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            return br.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }
    }

    private static void checkCommit(String commit) throws IOException, InterruptedException, ExecutionException {
        List<String> parentCommits = parentCommitsOf(commit);
        if (parentCommits.size() != 2) {
            System.out.println(commit + " is not a merge commit we're looking for. Parents: " + parentCommits);
            return;
        }

        List<String> commitBranches = branchesOf(commit);
        if (commitBranches.contains("origin/release")) {
            System.out.println(commit + " is a merge commit already on release, ignoring.");
            System.out.println("  Branches: " + commitBranches);
            return;
        }

        // The correct state we are looking for is:
        // 1. It's a merge commit.
        // 2. One of its parent commits is from master only.
        // 3. Another parent commit is not from master but from release branch.
        // Otherwise, skip this commit.
        List<String> p1Branches = branchesOf(parentCommits.get(0));
        List<String> p2Branches = branchesOf(parentCommits.get(1));

        System.out.println(commit + " parents: " + parentCommits);
        System.out.println(" p1Branches: " + p1Branches);
        System.out.println(" p2Branches: " + p2Branches);

        boolean p1IsMaster = p1Branches.contains("origin/master");
        boolean p2IsMaster = p2Branches.contains("origin/master");
        boolean p2IsRelease = p2Branches.stream().anyMatch(b -> b.startsWith("origin/release"));

        if (p1IsMaster && !p2IsMaster && p2IsRelease) {
            List<String> badFiles = filesFromMerge(commit).stream()
                .filter(gitFile -> MONITORED_PATHS.stream().anyMatch(forbiddenPath -> gitFile.startsWith(forbiddenPath)))
                .collect(Collectors.toList());

            if (!badFiles.isEmpty()) {
                System.err.println("Found bad files in merge commit " + commit + ", run the listed commands:");
                for (String f : badFiles) {
                    System.err.println("git restore --source=master -SW -- '" + f + "'");
                }
                System.err.println("And then amend the merge commit to remove all offending changes.");
                System.exit(1);
            } else {
                System.out.println(" -> No bad files found");
            }
        } else {
            System.out.println(" -> is not a merge commit we're looking for.");
        }
    }

    private static List<String> filesFromMerge(String commit) throws IOException, InterruptedException, ExecutionException {
        // Git revision range syntax is part of git, not the shell; pass as-is.
        return getStdoutLines(new String[] {"git", "diff", "--name-only", commit + "^1.." + commit});
    }

    private static List<String> branchesOf(String commit) throws IOException, InterruptedException, ExecutionException {
        List<String> lines = getStdoutLines(new String[] {"git", "branch", "-r", "--contains", commit});
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String s = line.replace("*", "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<String> parentCommitsOf(String commit) throws IOException, InterruptedException, ExecutionException {
        String stdout = getStdout(new String[] {"git", "show", "--format=%P", "--no-patch", "--no-show-signature", commit});
        String trimmed = stdout.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(trimmed.split("\\s+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private record ExecResult(
        String stdout,
        String stderr,
        int returnCode
    ) {
        @Override
        public String toString() {
            return "ExecResult{returnCode=" + returnCode + ", stdout=" + summarize(stdout) + ", stderr=" + summarize(stderr) + "}";
        }

        private static String summarize(String s) {
            if (s == null) return "null";
            String t = s.replace("\n", "\\n");
            if (t.length() > 500) {
                return t.substring(0, 500) + "...(truncated)";
            }
            return t;
        }
    }

    private static ExecResult exec(String[] command) throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(command, "command");
        if (command.length == 0) {
            throw new IllegalArgumentException("command must not be empty");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        Future<String> stdoutFuture = readStreamAsync(process.getInputStream());
        Future<String> stderrFuture = readStreamAsync(process.getErrorStream());

        int returnCode = process.waitFor();
        String stdout = stdoutFuture.get();
        String stderr = stderrFuture.get();
        return new ExecResult(stdout, stderr, returnCode);
    }

    private static String getStdout(String[] command) throws IOException, InterruptedException, ExecutionException {
        ExecResult execResult = exec(command);
        if (execResult.returnCode() != 0) {
            throw new AssertionError(String.join(" ", command) + " failed with return code: " + execResult);
        }
        return execResult.stdout();
    }

    private static List<String> getStdoutLines(String[] command) throws IOException, InterruptedException, ExecutionException {
        String stdout = getStdout(command);
        return Arrays.stream(stdout.split("\\R"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private static Future<String> readStreamAsync(InputStream inputStream) {
        return THREAD_POOL.submit((Callable<String>) () -> readFully(inputStream));
    }

    private static String readFully(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}

