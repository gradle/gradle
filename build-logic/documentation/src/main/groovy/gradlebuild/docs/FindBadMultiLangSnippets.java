/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Checks adoc files for reversed multi-language snippet order.
 * Only rule enforced:
 *   In a ==== ... ==== block, if there are at least two [.multi-language-sample] snippets,
 *   flag when the first is GROOVY and the second is KOTLIN.
 */
@CacheableTask
public abstract class FindBadMultiLangSnippets extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocumentationRoot();

    @TaskAction
    public void checkMultiLanguageSnippets() {
        Map<File, List<Error>> errors = new TreeMap<>();

        getDocumentationRoot().getAsFileTree().matching(spec -> spec.include("**/*.adoc")).forEach(file -> {
            gatherBadSnippetsInFile(file, errors);
        });

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Found reversed multi-language snippet order in AsciiDoc files:\n");
            for (Map.Entry<File, List<Error>> e : errors.entrySet()) {
                sb.append(" - ").append(e.getKey().getPath()).append("\n");
                for (Error err : e.getValue()) {
                    sb.append("    line ").append(err.line).append(": ").append(err.message).append("\n");
                }
            }
            throw new GradleException(sb.toString());
        }
    }

    private static final java.util.regex.Pattern SOURCE_LANG_PATTERN =
        java.util.regex.Pattern.compile(
            "^\\[\\s*source(?:%[\\w-]+)*\\s*,\\s*([\\w.+-]+)\\s*(?:,.*)?\\]$",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

    private void gatherBadSnippetsInFile(File file, Map<File, List<Error>> errors) {
        List<String> lines = new ArrayList<>();
        try (java.util.stream.Stream<String> stream = Files.lines(file.toPath())) {
            stream.forEach(lines::add);
        } catch (IOException ex) {
            // If a file can't be read, add an error
            addError(errors, file, new Error(1, "Failed to read file: " + ex.getMessage()));
            return;
        }

        boolean inExample = false; // between ==== ... ====
        List<Snippet> currentSnippets = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();

            if (line.equals("====")) {  // first ====
                // Toggle example block
                if (!inExample) {
                    inExample = true;
                    currentSnippets.clear();
                } else {
                    // Closing an example block: check for reversed order only
                    flagIfReversed(file, errors, currentSnippets);
                    inExample = false;
                    currentSnippets.clear();
                }
                continue;
            }

            if (!inExample) {
                continue;
            }

            if (line.equals("[.multi-language-sample]")) { // first [.multi-language-sample]
                int headerLine = i + 1; // 1-based
                // Look ahead to find [source, ...] until next sample or end of example block
                String sourceLang = null;
                for (int j = i + 1; j < lines.size(); j++) {
                    String look = lines.get(j).trim();

                    if (look.equals("[.multi-language-sample]") || look.equals("====")) {
                        break; // stop at next sample or end of example block
                    }

                    if (sourceLang == null) {
                        String parsed = parseSourceLang(look);
                        if (parsed != null) {
                            sourceLang = parsed;
                            break; // found the language for this sample
                        }
                    }
                }

                Language detected = deduceLanguage(sourceLang);
                currentSnippets.add(new Snippet(headerLine, detected));
            }
        }

        // If file ends inside an example, still check that block
        if (inExample) {
            flagIfReversed(file, errors, currentSnippets);
        }
    }

    /**
     * Only flags when the first two detectable snippets in the block are GROOVY then KOTLIN.
     * Ignores blocks with fewer than two detectable snippets.
     */
    private void flagIfReversed(File file, Map<File, List<Error>> errors, List<Snippet> snippets) {
        List<Snippet> pair = snippets.stream()
            .filter(s -> s.lang != Language.UNKNOWN)
            .limit(2)
            .toList();

        if (pair.size() == 2 &&
            pair.get(0).lang == Language.GROOVY &&
            pair.get(1).lang == Language.KOTLIN) {
            addError(errors, file, new Error(pair.get(0).line,
                "Reversed order: found Groovy first then Kotlin. Expected Kotlin first then Groovy."));
        }
    }

    private static String parseSourceLang(String lineTrimmed) {
        var m = SOURCE_LANG_PATTERN.matcher(lineTrimmed);
        if (m.matches()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static Language deduceLanguage(String sourceLang) {
        if (sourceLang != null) {
            if ("kotlin".equalsIgnoreCase(sourceLang)) {
                return Language.KOTLIN;
            }
            if ("groovy".equalsIgnoreCase(sourceLang)) {
                return Language.GROOVY;
            }
        }
        return Language.UNKNOWN;
    }

    private static void addError(Map<File, List<Error>> errors, File file, Error error) {
        errors.computeIfAbsent(file, f -> new ArrayList<>()).add(error);
    }

    private enum Language { KOTLIN, GROOVY, UNKNOWN }

    private static final class Snippet {
        final int line;       // line of the [.multi-language-sample] header
        final Language lang;  // best-effort deduction (UNKNOWN allowed)

        Snippet(int line, Language lang) {
            this.line = line;
            this.lang = lang;
        }
    }

    private static final class Error {
        final int line;
        final String message;

        Error(int line, String message) {
            this.line = line;
            this.message = message;
        }
    }
}
