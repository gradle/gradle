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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TeamCity helper script that fails the build when any commit in the PR introduces a git submodule.
 *
 * Rationale: the project does not want a dependency on git submodules anywhere in its history. A submodule
 * that only exists in an intermediate commit still breaks bisecting, worktree creation and shallow clones,
 * so the whole range is inspected, not just the PR head.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckNoSubmodules.java [--warn-only] &lt; commits.txt
 *
 * Reads commit SHAs from stdin (one per line) and looks at two things, both at constant or
 * change-proportional cost so the step stays cheap on long branches:
 *
 * - one `git diff-tree` over the whole range, which reports every commit that adds or updates a
 *   gitlink (mode 160000) or a .gitmodules file. This is what catches a submodule that an
 *   intermediate commit adds and a later commit reverts.
 * - one `git ls-tree` per range head, which catches a submodule inherited from the target branch
 *   and still present at the PR head. A submodule the PR *removes* is deliberately not a finding.
 *
 * With --warn-only the findings are reported as a TeamCity warning and the exit code stays 0; a git
 * failure is downgraded to a warning too. The caller passes it for work-in-progress builds (plain
 * branch builds and draft PRs) so a submodule shows up as early feedback without blocking, while
 * ready-for-review PRs and merge queue builds run in enforcing mode and fail.
 */
public class CheckNoSubmodules {
    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private static final String GITLINK_MODE = "160000";
    private static final String ABSENT_MODE = "000000";
    private static final String GITMODULES_FILE_NAME = ".gitmodules";

    private static final Pattern COMMIT_LINE = Pattern.compile("^[0-9a-f]{40}$");

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } finally {
            THREAD_POOL.shutdown();
        }
    }

    private static void run(String[] args) throws Exception {
        boolean warnOnly = false;
        for (String arg : args) {
            if ("--warn-only".equals(arg)) {
                warnOnly = true;
            } else {
                System.err.println("Unknown argument: " + arg);
                System.err.println("Usage: java CheckNoSubmodules.java [--warn-only] < commits.txt");
                System.exit(2);
            }
        }

        List<String> commits;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            commits = br.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        if (commits.isEmpty()) {
            System.out.println("No commits to check.");
            return;
        }

        Set<String> violations;
        try {
            violations = findViolations(commits);
        } catch (Exception e) {
            // A git hiccup must never fail a work-in-progress build: LightweightChecks runs with
            // failStage = true, so exiting non-zero here also drops every downstream stage.
            if (!warnOnly) {
                System.err.println("Failed to check for git submodules: " + e.getMessage());
                System.exit(1);
            }
            warning("Could not check for git submodules: " + e.getMessage());
            System.out.println("Not failing this build (--warn-only).");
            return;
        }

        if (!violations.isEmpty()) {
            report(violations, warnOnly);
            if (warnOnly) {
                // Deliberately successful: work-in-progress builds only get early feedback.
                return;
            }
            System.exit(1);
        }

        System.out.println("Checked " + commits.size() + " commit(s); no git submodules found.");
    }

    private static Set<String> findViolations(List<String> commits) throws IOException, InterruptedException, ExecutionException {
        Set<String> violations = new LinkedHashSet<>();
        // Paths already reported from the range scan; keeps the head scan from repeating them.
        Set<String> reportedPaths = new LinkedHashSet<>();

        // diff-tree prints the commit id on its own line ahead of that commit's changes, so a
        // finding can name the commit to amend rather than just the range.
        String currentCommit = null;
        for (String line : diffTree(commits)) {
            if (COMMIT_LINE.matcher(line).matches()) {
                currentCommit = line;
                continue;
            }
            RawChange change = RawChange.parse(line);
            if (change == null) {
                continue;
            }
            String finding = change.describeIntroduction();
            if (finding != null) {
                violations.add("Commit " + currentCommit + " " + finding);
                reportedPaths.add(change.path);
            }
        }

        for (String head : rangeHeads(commits)) {
            for (String[] entry : lsTree(head)) {
                String mode = entry[0];
                String path = entry[1];
                if (reportedPaths.contains(path)) {
                    continue;
                }
                if (GITLINK_MODE.equals(mode)) {
                    violations.add("PR head " + head + " contains a submodule (gitlink) at '" + path + "'");
                } else if (isGitmodules(path)) {
                    violations.add("PR head " + head + " contains a " + GITMODULES_FILE_NAME + " file at '" + path + "'");
                }
            }
        }

        return violations;
    }

    /**
     * One raw line of `git diff-tree --raw` output:
     * {@code :<srcmode> <dstmode> <srcsha> <dstsha> <status>\t<path>}.
     */
    private static final class RawChange {
        final String srcMode;
        final String dstMode;
        final String path;

        private RawChange(String srcMode, String dstMode, String path) {
            this.srcMode = srcMode;
            this.dstMode = dstMode;
            this.path = path;
        }

        static RawChange parse(String line) {
            if (line.isEmpty() || line.charAt(0) != ':') {
                return null;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                return null;
            }
            // Rename / copy lines carry two paths; the destination is the last one.
            String path = line.substring(line.lastIndexOf('\t') + 1);
            String[] fields = line.substring(1, tab).split(" ");
            if (fields.length < 2) {
                return null;
            }
            return new RawChange(fields[0], fields[1], path);
        }

        /**
         * A finding when this change puts a submodule reference into the tree, null otherwise.
         * A change that only *removes* one is fine - that is a PR cleaning up after an earlier mistake.
         */
        String describeIntroduction() {
            if (GITLINK_MODE.equals(dstMode)) {
                String verb = GITLINK_MODE.equals(srcMode) ? "updates" : "adds";
                return verb + " a submodule (gitlink) at '" + path + "'";
            }
            if (isGitmodules(path) && !ABSENT_MODE.equals(dstMode)) {
                return "adds or modifies a " + GITMODULES_FILE_NAME + " file at '" + path + "'";
            }
            return null;
        }
    }

    private static boolean isGitmodules(String path) {
        return path.equals(GITMODULES_FILE_NAME) || path.endsWith("/" + GITMODULES_FILE_NAME);
    }

    /**
     * Raw change lines plus commit-id lines for the whole range, in a single git invocation.
     * -m makes merge commits report against each parent, so a submodule merged in is caught too.
     */
    private static List<String> diffTree(List<String> commits) throws IOException, InterruptedException, ExecutionException {
        String out = stdout(String.join("\n", commits) + "\n", "git", "diff-tree", "--stdin", "-r", "-m", "--raw");
        return Arrays.stream(out.split("\n"))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * The commits in the range that no other commit in it descends from - the PR head for a normal
     * branch. Derived from the commit list rather than assuming an order on it.
     */
    private static List<String> rangeHeads(List<String> commits) throws IOException, InterruptedException, ExecutionException {
        List<String> cmd = new ArrayList<>(List.of("git", "merge-base", "--independent"));
        cmd.addAll(commits);
        String out = stdout(null, cmd.toArray(new String[0]));
        return Arrays.stream(out.split("\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * {mode, path} for every entry in the commit's tree.
     */
    private static List<String[]> lsTree(String commit) throws IOException, InterruptedException, ExecutionException {
        String out = stdout(null, "git", "ls-tree", "-r", "--full-tree", commit);
        List<String[]> entries = new ArrayList<>();
        for (String line : out.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String[] meta = line.substring(0, tab).split(" ");
            if (meta.length > 0) {
                entries.add(new String[] {meta[0], line.substring(tab + 1)});
            }
        }
        return entries;
    }

    private static void warning(String message) {
        // Surface it in the TeamCity build log as a warning so it doesn't drown in the step output.
        System.out.println("##teamcity[message text='" + message.replace("'", "|'") + "' status='WARNING']");
    }

    private static void report(Set<String> violations, boolean warnOnly) {
        PrintStream out = warnOnly ? System.out : System.err;
        if (warnOnly) {
            warning(
                "Git submodules are not allowed in this repository."
                    + " This build only warns; the check fails once the PR is ready for review."
            );
        }
        out.println("Git submodules are not allowed in this repository.");
        out.println("Offending findings:");
        for (String v : violations) {
            out.println("  - " + v);
        }
        out.println();
        out.println("Please remove the submodule (git rm <path>, drop " + GITMODULES_FILE_NAME + ")");
        out.println("and vendor or depend on the sources another way.");
        out.println("Since the whole range is checked, rebase / amend the offending commit(s), then force-push.");
        if (warnOnly) {
            out.println();
            out.println("Not failing this build (--warn-only): draft PR or plain branch build.");
            out.println("This will fail once the PR is ready for review, and in the merge queue.");
        }
    }

    private static String stdout(String input, String... cmd) throws IOException, InterruptedException, ExecutionException {
        ExecResult r = exec(input, cmd);
        if (r.exitCode != 0) {
            throw new IllegalStateException(String.join(" ", cmd) + " failed: " + r);
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

    private static ExecResult exec(String input, String... cmd) throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(cmd, "cmd");
        if (cmd.length == 0) throw new IllegalArgumentException("cmd must not be empty");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        // stdin is written and both streams are drained concurrently: diff-tree output runs to
        // megabytes, which deadlocks if we write the whole commit list before starting to read.
        Future<?> stdinFuture = THREAD_POOL.submit(() -> {
            try (OutputStream os = p.getOutputStream()) {
                if (input != null) {
                    os.write(input.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // The process may exit before consuming all input; its exit code is what matters.
            }
            return null;
        });
        Future<String> outFuture = readStreamAsync(p.getInputStream());
        Future<String> errFuture = readStreamAsync(p.getErrorStream());
        int code = p.waitFor();
        stdinFuture.get();
        return new ExecResult(code, outFuture.get(), errFuture.get());
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
