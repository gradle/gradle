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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TeamCity helper script to validate that each commit in a PR uses only released Gradle versions in the wrapper.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckWrapper.java <target_branch_name>
 *   java .teamcity/scripts/CheckWrapper.java -   # read commits from stdin (one SHA per line), checks wrapper per commit
 *
 * It determines the PR range from the current HEAD:
 * - Uses origin/<target_branch_name> as the target
 * - If HEAD is a merge commit and one parent equals the target SHA, the other parent is treated as PR head
 * - Otherwise HEAD itself is treated as PR head
 *
 * Then it iterates commits in BASE..PR_HEAD and checks gradle/wrapper/gradle-wrapper.properties.
 */
public class CheckWrapper {
    private static final Pattern ALLOWED_WRAPPER_VERSION =
        Pattern.compile("^[0-9.]+(-(rc|milestone|m)-[0-9]+)?$");

    // Keep the same extraction semantics as the old sed:
    //   sed 's/.*gradle-\(.*\)-[a-z]*\.[a-z]*/\1/'
    private static final Pattern WRAPPER_VERSION_EXTRACT =
        Pattern.compile(".*gradle-(.*)-[a-z]*\\.[a-z]*");

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java CheckWrapper.java <target_branch_name>");
            System.exit(2);
        }

        String targetBranch = args[0].trim();
        if (targetBranch.isEmpty()) {
            System.err.println("target_branch_name must not be empty");
            System.exit(2);
        }

        if ("-".equals(targetBranch)) {
            checkCommitsFromStdin();
            return;
        }

        String targetRef = "refs/remotes/origin/" + targetBranch;

        if (!refExists(targetRef)) {
            System.out.println("Target ref " + targetRef + " not present locally; fetching origin/" + targetBranch + "...");
            run("git", "fetch", "origin", targetBranch);
        }

        String targetSha = stdout("git", "rev-parse", targetRef).trim();
        String headSha = stdout("git", "rev-parse", "HEAD").trim();

        List<String> parents =
            Arrays.stream(stdout("git", "show", "--no-patch", "--format=%P", headSha).trim().split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String prHead = headSha;
        if (parents.size() >= 2) {
            String p1 = parents.get(0);
            String p2 = parents.get(1);
            if (p1.equals(targetSha)) {
                prHead = p2;
            } else if (p2.equals(targetSha)) {
                prHead = p1;
            }
        }

        String baseSha = stdout("git", "merge-base", targetSha, prHead).trim();

        System.out.println("Target branch: " + targetBranch + " (" + targetSha + ")");
        System.out.println("PR head: " + prHead);
        System.out.println("Base: " + baseSha);

        List<String> commits =
            Arrays.stream(stdout("git", "rev-list", baseSha + ".." + prHead).split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

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

        run("git", "checkout", prHead, "--quiet", "--detach");
    }

    private static void checkCommitsFromStdin() throws IOException, InterruptedException {
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

    private static boolean refExists(String ref) throws IOException, InterruptedException {
        ExecResult r = exec("git", "rev-parse", "--verify", ref);
        return r.exitCode == 0;
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

    private static void run(String... cmd) throws IOException, InterruptedException {
        ExecResult r = exec(cmd);
        if (r.exitCode != 0) {
            throw new AssertionError(String.join(" ", cmd) + " failed: " + r);
        }
    }

    private static String stdout(String... cmd) throws IOException, InterruptedException {
        ExecResult r = exec(cmd);
        if (r.exitCode != 0) {
            throw new AssertionError(String.join(" ", cmd) + " failed: " + r);
        }
        return r.stdout;
    }

    private static final class ExecResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ExecResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

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

    private static ExecResult exec(String... cmd) throws IOException, InterruptedException {
        Objects.requireNonNull(cmd, "cmd");
        if (cmd.length == 0) throw new IllegalArgumentException("cmd must not be empty");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        String out = readFully(p.getInputStream());
        String err = readFully(p.getErrorStream());
        int code = p.waitFor();
        return new ExecResult(code, out, err);
    }

    private static String readFully(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = input.read(buf)) >= 0) {
                out.write(buf, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}

