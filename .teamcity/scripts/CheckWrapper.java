/*
 * Copyright 2026
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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * TeamCity helper script to validate that each commit in a PR uses only released Gradle versions in the wrapper.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckWrapper.java < commits.txt
 *
 * Reads commit SHAs from stdin (one per line), checks wrapper per commit.
 */
public class CheckWrapper {
    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private static final Pattern ALLOWED_WRAPPER_VERSION =
        Pattern.compile("^[0-9.]+(-(rc|milestone|m)-[0-9]+)?$");

    // Keep the same extraction semantics as the old sed:
    //   sed 's/.*gradle-\(.*\)-[a-z]*\.[a-z]*/\1/'
    private static final Pattern WRAPPER_VERSION_EXTRACT =
        Pattern.compile(".*gradle-(.*)-[a-z]*\\.[a-z]*");

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            System.err.println("Usage: java CheckWrapper.java < commits.txt");
            System.exit(2);
        }

        try {
            checkCommitsFromStdin();
        } finally {
            THREAD_POOL.shutdown();
        }
    }

    private static void checkCommitsFromStdin() throws IOException, InterruptedException, ExecutionException {
        String originalHead = stdout("git", "rev-parse", "HEAD").trim();

        List<String> commits;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            commits = br.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        for (String commit : commits) {
            run("git", "checkout", commit, "--quiet", "--detach");
            String wrapperVersion = readWrapperVersion();
            System.out.println("Commit " + commit + " wrapper: " + wrapperVersion);
            if (!ALLOWED_WRAPPER_VERSION.matcher(wrapperVersion).matches()) {
                System.err.println(
                    "Bad wrapper version " + wrapperVersion + " used in commit " + commit
                        + ". Please rebase your branch to ensure that each commit uses only released Gradle versions in wrapper (GA, RC or milestone)."
                );
                System.exit(1);
            }
        }

        run("git", "checkout", originalHead, "--quiet", "--detach");
    }

    private static String readWrapperVersion() throws IOException {
        Path props = Paths.get("gradle/wrapper/gradle-wrapper.properties");
        List<String> lines = Files.readAllLines(props, StandardCharsets.UTF_8);
        String distributionUrl =
            lines.stream()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .filter(l -> l.startsWith("distributionUrl"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("distributionUrl not found in " + props));

        int idx = distributionUrl.indexOf('=');
        if (idx < 0) {
            throw new IllegalStateException("Malformed distributionUrl line: " + distributionUrl);
        }
        String url = distributionUrl.substring(idx + 1).trim();

        Matcher m = WRAPPER_VERSION_EXTRACT.matcher(url);
        if (!m.matches()) {
            throw new IllegalStateException("Could not extract wrapper version from distributionUrl: " + url);
        }
        return m.group(1);
    }

    private static void run(String... cmd) throws IOException, InterruptedException, ExecutionException {
        ExecResult r = exec(cmd);
        if (r.exitCode() != 0) {
            throw new AssertionError(String.join(" ", cmd) + " failed: " + r);
        }
    }

    private static String stdout(String... cmd) throws IOException, InterruptedException, ExecutionException {
        ExecResult r = exec(cmd);
        if (r.exitCode() != 0) {
            throw new AssertionError(String.join(" ", cmd) + " failed: " + r);
        }
        return r.stdout();
    }

    private record ExecResult(
        int exitCode,
        String stdout,
        String stderr
    ) {
        @Override
        public String toString() {
            return "exitCode=" + exitCode + ", stdout=" + summarize(stdout) + ", stderr=" + summarize(stderr);
        }

        private static String summarize(String s) {
            if (s == null) return "null";
            String t = s.replace("\n", "\\n");
            if (t.length() > 500) return t.substring(0, 500) + "...(truncated)";
            return t;
        }
    }

    private static ExecResult exec(String... cmd) throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(cmd, "cmd");
        if (cmd.length == 0) throw new IllegalArgumentException("cmd must not be empty");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        Future<String> outFuture = readStreamAsync(p.getInputStream());
        Future<String> errFuture = readStreamAsync(p.getErrorStream());
        int code = p.waitFor();
        String out = outFuture.get();
        String err = errFuture.get();
        return new ExecResult(code, out, err);
    }

    private static Future<String> readStreamAsync(InputStream inputStream) {
        return THREAD_POOL.submit((Callable<String>) () -> readFully(inputStream));
    }

    private static String readFully(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = input.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}

