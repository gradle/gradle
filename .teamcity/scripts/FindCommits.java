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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TeamCity helper script that prints the list of commits in the current PR (one SHA per line).
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/FindCommits.java <target_branch_name>
 *
 * Commit range logic:
 * - Uses origin/<target_branch_name> as the target
 * - If HEAD is a merge commit and one parent equals the target SHA, the other parent is treated as PR head
 * - Otherwise HEAD itself is treated as PR head
 *
 * Output:
 * - Writes commit SHAs to stdout, one per line
 * - Diagnostic info to stderr
 */
public class FindCommits {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java FindCommits.java <target_branch_name>");
            System.exit(2);
        }

        String targetBranch = args[0].trim();
        if (targetBranch.isEmpty()) {
            System.err.println("target_branch_name must not be empty");
            System.exit(2);
        }

        String targetRef = "refs/remotes/origin/" + targetBranch;

        if (!refExists(targetRef)) {
            System.err.println("Target ref " + targetRef + " not present locally; fetching origin/" + targetBranch + "...");
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

        System.err.println("Target branch: " + targetBranch + " (" + targetSha + ")");
        System.err.println("PR head: " + prHead);
        System.err.println("Base: " + baseSha);

        // One SHA per line to stdout so callers can pipe safely.
        System.out.print(stdout("git", "rev-list", baseSha + ".." + prHead));
    }

    private static boolean refExists(String ref) throws IOException, InterruptedException {
        ExecResult r = exec("git", "rev-parse", "--verify", ref);
        return r.exitCode == 0;
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

