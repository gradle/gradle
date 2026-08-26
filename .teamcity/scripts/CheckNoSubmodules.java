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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TeamCity helper script that fails the build when any commit in the PR introduces a git submodule.
 *
 * Rationale: the project does not want a dependency on git submodules anywhere in its history. A submodule
 * that only exists in an intermediate commit still breaks bisecting, worktree creation and shallow clones,
 * so every commit in the range is inspected, not just the PR head.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckNoSubmodules.java &lt; commits.txt
 *
 * Reads commit SHAs from stdin (one per line). For every commit the tree is scanned for
 * gitlink entries (mode 160000) and for a .gitmodules file at any depth. Exits 0 if all
 * commits are clean, 1 if any submodule reference is detected.
 */
public class CheckNoSubmodules {
    private static final String GITLINK_MODE = "160000";
    private static final String GITMODULES_FILE_NAME = ".gitmodules";

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            System.err.println("Usage: java CheckNoSubmodules.java < commits.txt");
            System.exit(2);
        }

        List<String> commits;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            commits = br.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        List<String> violations = new ArrayList<>();

        if (commits.isEmpty()) {
            System.out.println("No commits to check.");
        } else {
            for (String commit : commits) {
                for (String finding : findSubmoduleReferences(commit)) {
                    violations.add("Commit " + commit + " " + finding);
                }
            }
        }

        if (!violations.isEmpty()) {
            System.err.println("Git submodules are not allowed in this repository.");
            System.err.println("Offending findings:");
            for (String v : violations) {
                System.err.println("  - " + v);
            }
            System.err.println();
            System.err.println("Please remove the submodule (git rm <path>, drop " + GITMODULES_FILE_NAME + ")");
            System.err.println("and vendor or depend on the sources another way.");
            System.err.println("Since every commit is checked, rebase / amend the offending commit(s), then force-push.");
            System.exit(1);
        }

        System.out.println("Checked " + commits.size() + " commit(s); no git submodules found.");
    }

    /**
     * Returns a human readable finding per submodule reference in the given commit's tree, empty when clean.
     */
    private static List<String> findSubmoduleReferences(String commit) throws IOException, InterruptedException {
        // -r recurses into subtrees; gitlinks show up as "160000 commit <sha>\t<path>".
        String tree = stdout("git", "ls-tree", "-r", "--full-tree", commit);

        Set<String> gitlinkPaths = new LinkedHashSet<>();
        Set<String> gitmodulesPaths = new LinkedHashSet<>();
        for (String line : tree.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String path = line.substring(tab + 1);
            String[] meta = line.substring(0, tab).split(" ");
            if (meta.length > 0 && GITLINK_MODE.equals(meta[0])) {
                gitlinkPaths.add(path);
            }
            if (path.equals(GITMODULES_FILE_NAME) || path.endsWith("/" + GITMODULES_FILE_NAME)) {
                gitmodulesPaths.add(path);
            }
        }

        List<String> findings = new ArrayList<>();
        for (String path : gitlinkPaths) {
            findings.add("contains submodule (gitlink) entry at '" + path + "'");
        }
        for (String path : gitmodulesPaths) {
            findings.add("contains a " + GITMODULES_FILE_NAME + " file at '" + path + "'");
        }
        return findings;
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
