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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TeamCity helper script that fails the build when any commit in the PR carries an AI attribution footer
 * in its commit message (e.g. "Co-Authored-By: Claude", "🤖 Generated with Claude Code").
 *
 * Rationale: the project does not want AI attribution markers in its git history.
 * Local suppression lives in .claude/settings.json (attribution.commit / attribution.pr); this script
 * enforces the same policy server-side so contributors without those settings can't slip footers through.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckAiAttribution.java [--pr-body-file &lt;path&gt;] &lt; commits.txt
 *
 * Reads commit SHAs from stdin (one per line). Exits 0 if all commit messages are clean,
 * 1 if any forbidden footer is detected.
 *
 * When --pr-body-file is supplied and points to a readable file, the file's content is also
 * scanned for the same forbidden patterns. The caller is expected to pre-fetch the PR body
 * (e.g. via the GitHub REST API with jq) and pass the result through. The script silently
 * skips PR-body scanning when the path is unset, blank, or the file doesn't exist (so direct
 * branch / master builds with no associated PR don't fail).
 */
public class CheckAiAttribution {
    // Patterns are matched case-insensitively against each commit message.
    // Keep this list conservative; extend deliberately. The first match per commit is enough to fail.
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
        Pattern.compile("Co-Authored-By:.*Claude", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Co-Authored-By:.*noreply@anthropic\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Generated with .*Claude Code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("🤖 Generated with", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Co-Authored-By:.*Cursor", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Co-Authored-By:.*cursoragent@cursor\\.com", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Ma(de|ke) with .*Cursor", Pattern.CASE_INSENSITIVE)
    );

    public static void main(String[] args) throws Exception {
        String prBodyFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--pr-body-file":
                    if (++i >= args.length) {
                        System.err.println("Missing value for --pr-body-file");
                        System.exit(2);
                    }
                    prBodyFile = args[i];
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.err.println("Usage: java CheckAiAttribution.java [--pr-body-file <path>] < commits.txt");
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

        List<String> violations = new ArrayList<>();

        if (commits.isEmpty()) {
            System.out.println("No commits to check.");
        } else {
            for (String commit : commits) {
                String message = stdout("git", "log", "-1", "--format=%B", commit);
                Pattern match = firstMatch(message);
                if (match != null) {
                    violations.add("Commit " + commit + " matches forbidden pattern /" + match.pattern() + "/");
                }
            }
        }

        scanPrBodyFile(prBodyFile, violations);

        if (!violations.isEmpty()) {
            System.err.println("AI attribution footers are not allowed in this repository.");
            System.err.println("Offending findings:");
            for (String v : violations) {
                System.err.println("  - " + v);
            }
            System.err.println();
            System.err.println("Please remove AI attribution footers (e.g. \"Co-Authored-By: Claude\",");
            System.err.println("\"🤖 Generated with Claude Code\", \"Made with Cursor\") from commit messages and the PR description.");
            System.err.println("For commit messages, rebase / amend, then force-push.");
            System.err.println("See .claude/settings.json for the local-suppression half of this policy.");
            System.exit(1);
        }

        System.out.println("Checked " + commits.size() + " commit(s); no AI attribution footers found.");
    }

    private static Pattern firstMatch(String text) {
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return pattern;
            }
        }
        return null;
    }

    private static void scanPrBodyFile(String prBodyFile, List<String> violations) throws IOException {
        if (prBodyFile == null || prBodyFile.isEmpty()) {
            return;
        }
        Path path = Paths.get(prBodyFile);
        if (!Files.isRegularFile(path)) {
            System.out.println("PR body file " + prBodyFile + " not present; skipping PR body scan.");
            return;
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.isEmpty()) {
            System.out.println("PR body file " + prBodyFile + " is empty; skipping PR body scan.");
            return;
        }
        Pattern match = firstMatch(content);
        if (match != null) {
            violations.add("PR body matches forbidden pattern /" + match.pattern() + "/");
        } else {
            System.out.println("Scanned PR body (" + content.length() + " chars); clean.");
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
