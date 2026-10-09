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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks adoc files for broken links.
 */
@CacheableTask
public abstract class FindBrokenInternalLinks extends DefaultTask {

    // <<groovy_plugin.adoc#groovy_plugin,Groovy>>
    private final Pattern linkPattern = Pattern.compile("<<([^,>]+)[^>]*>>");
    // groovy_plugin.adoc#groovy_plugin,Groovy
    private final Pattern linkWithHashPattern = Pattern.compile("([a-zA-Z_0-9-.]*)#(.*)");
    // A `++...++` passthrough, or any character that does not end a link macro target
    private static final String LINK_TARGET_CHAR = "(?:\\+\\+.*?\\+\\+|[^\\[\\s])";
    // link:{javadocPath}/org/gradle/api/Task.html#dependsOn(java.lang.Object++...++)[Task.dependsOn()]
    private final Pattern javadocLinkPattern = Pattern.compile("link:\\{(?i:javadocPath)\\}/(" + LINK_TARGET_CHAR + "*?\\.html)(?:#(" + LINK_TARGET_CHAR + "*))?");
    // link:{groovyDslPath}/org.gradle.api.Project.html#org.gradle.api.Project:files(java.lang.Object++[]++)[Project.files()]
    private final Pattern dslLinkPattern = Pattern.compile("link:\\{(?i:groovyDslPath)\\}/(" + LINK_TARGET_CHAR + "*?\\.html)(?:#(" + LINK_TARGET_CHAR + "*))?");
    // link:{javaApi}/java/lang/String.html#format(java.lang.String,java.lang.Object++...++)[String.format()]
    private final Pattern linkMacroTargetPattern = Pattern.compile("link:(" + LINK_TARGET_CHAR + "*)\\[");
    // link:https://kotlinlang.org/docs/reference/using-gradle.html#targeting-the-jvm[Kotlin]
    private final Pattern markdownLinkPattern = Pattern.compile("\\[[^]]+]\\([^)^\\\\]+\\)");

    // <a href="javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html">
    // [`getName()`](javadoc/org/gradle/api/Named.html#getName())
    private final Pattern releaseNotesJavadocPattern = Pattern.compile("javadoc/(.*?\\.html)(?:#([^\\s\"'<>]*))?");
    // [`Sync`](dsl/org.gradle.api.tasks.Sync.html), but neither kotlin-dsl/... nor javadoc/.../dsl/...
    private final Pattern releaseNotesDslPattern = Pattern.compile("(?<=[(\"'])dsl/(.*?\\.html)(?:#([^\\s\"'<>]*))?");
    // <a href="userguide/upgrading_version_8.html#changes_@baseVersion@">
    private final Pattern releaseNotesUserGuidePattern = Pattern.compile("userguide/(.*?)(?=\\.html)");

    // <section class="detail" id="dependsOn(java.lang.Object...)"> or <a name="org.gradle.api.Task:dependsOn(java.lang.Object[])">, but not <meta name="description">
    private final Pattern anchorTargetPattern = Pattern.compile("(?<![\\w-])id=\"([^\"]+)\"|<a\\s[^>]*?\\bname=\"([^\"]+)\"");
    // &#91; or &#x5B;
    private final Pattern numericCharacterReferencePattern = Pattern.compile("&#(?:(\\d+)|[xX]([0-9a-fA-F]+));");
    private final Map<File, Set<String>> anchorTargetsByFile = new HashMap<>();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocumentationRoot();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getJavadocRoot();

    /**
     * Root of the rendered Groovy DSL reference; links into it are not checked when it is absent.
     */
    @Optional @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDslRoot();

    @Optional @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getReleaseNotesFile();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void checkDeadLinks() {
        if (!getDslRoot().isPresent()) {
            getLogger().lifecycle("The DSL reference was not generated, links into it are not checked");
        }
        Map<File, List<Error>> errors = new TreeMap<>();

        gatherDeadLinksInFileReleaseNotes(errors);

        getDocumentationRoot().getAsFileTree().matching(pattern -> pattern.include("**/*.adoc")).forEach(file -> {
            gatherDeadLinksInFile(file, errors);
        });

        reportErrors(errors, getReportFile().get().getAsFile());
    }

    private void reportErrors(Map<File, List<Error>> errors, File reportFile) {
        try (PrintWriter fw = new PrintWriter(new FileWriter(reportFile))) {
            writeHeader(fw);
            if (errors.isEmpty()) {
                fw.println("All clear!");
                return;
            }
            for (Map.Entry<File, List<Error>> e : errors.entrySet()) {
                File file = e.getKey();
                List<Error> errorsForFile = e.getValue();

                StringBuilder sb = new StringBuilder();
                for (Error error : errorsForFile) {
                    sb.append("ERROR: " + file.getName() + ":" + error.lineNumber + " " + error.message + "\n    " + error.line + "\n");
                }
                String message = sb.toString();
                getLogger().error(message);
                fw.println(message);
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        throw new GradleException("Documentation assertion failed: found invalid internal links. See " + new org.gradle.internal.logging.ConsoleRenderer().asClickableFileUrl(reportFile));
    }

    private void writeHeader(PrintWriter fw) {
        fw.println("# Valid links are:");
        fw.println("# * Inside the same file: <<(#)section-name(,text)>>");
        fw.println("# * To a different file: <<other-file(.adoc)#section-name,text>> - Note that the # and section are mandatory, otherwise the link is invalid in the single page output");
        fw.println("#");
        fw.println("# The checker does not handle implicit section names, so they must be explicit and declared as: [[section-name]]");
        fw.println("#");
        fw.println("# The checker also rejects Markdown-style links, such as [text](https://example.com/something) as they do not render properly");

    }

    private void gatherDeadLinksInFileReleaseNotes(Map<File, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();
        File sourceFile = getReleaseNotesFile().get().getAsFile();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                gatherDeadUserGuideLinksInLineReleaseNotes(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadReferenceLinksInLine(releaseNotesJavadocPattern, javadoc(), true, sourceFile, line, lineNumber, errorsForFile);
                gatherDeadReferenceLinksInLine(releaseNotesDslPattern, dsl(), true, sourceFile, line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile, errorsForFile);
        }
    }

    private void gatherDeadUserGuideLinksInLineReleaseNotes(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = releaseNotesUserGuidePattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1);
            String fileName = getFileName(link, sourceFile);
            File referencedFile = new File(getDocumentationRoot().get().getAsFile(), fileName);
            if (!referencedFile.exists()) {
                    errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + fileName));
            }
        }
    }

    private void gatherDeadLinksInFile(File sourceFile, Map<File, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                gatherDeadLinksInLine(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadReferenceLinksInLine(javadocLinkPattern, javadoc(), false, sourceFile, line, lineNumber, errorsForFile);
                gatherDeadReferenceLinksInLine(dslLinkPattern, dsl(), false, sourceFile, line, lineNumber, errorsForFile);
                gatherMarkdownLinksInLine(sourceFile, line, lineNumber, errorsForFile);
                gatherMangledLinkTargetsInLine(line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile, errorsForFile);
        }
    }

    private void gatherMarkdownLinksInLine(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = markdownLinkPattern.matcher(line);
        while (matcher.find()) {
             String invalidLink = matcher.group();
             errorsForFile.add(new Error(lineNumber, line, "Markdown-style links are not supported: " + invalidLink));
        }
    }

    private void gatherMangledLinkTargetsInLine(String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = linkMacroTargetPattern.matcher(line);
        while (matcher.find()) {
            String target = matcher.group(1);
            if (target.startsWith("link:")) {
                errorsForFile.add(new Error(lineNumber, line, "Doubled `link:` macro in link target " + target));
            }
            // Asciidoctor renders a bare `...` as an ellipsis character, unless it is wrapped in a `++` passthrough
            if (target.replaceAll("\\+\\+.*?\\+\\+", "").contains("...")) {
                errorsForFile.add(new Error(lineNumber, line, "Unescaped `...` in link target " + target + " (write `++...++` instead)"));
            }
        }
    }

    private void gatherDeadLinksInLine(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = linkPattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1);
            if (link.contains("#")) {
                Matcher linkMatcher = linkWithHashPattern.matcher(link);
                if (linkMatcher.matches()) {
                    MatchResult result = linkMatcher.toMatchResult();
                    String fileName = getFileName(result.group(1), sourceFile);
                    File referencedFile = new File(getDocumentationRoot().get().getAsFile(), fileName);
                    if (!referencedFile.exists() || referencedFile.isDirectory()) {
                        errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + fileName));
                    } else {
                        String idName = result.group(2);
                        if (idName.isEmpty()) {
                            errorsForFile.add(new Error(lineNumber, line, "Missing section reference for link to " + fileName));
                        } else {
                            if (!fileContainsText(referencedFile, "[[" + idName + "]]")) {
                                errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + idName + " in " + fileName));
                            }
                        }
                    }
                }
            } else {
                if (!fileContainsText(sourceFile, "[[" + link + "]]")) {
                    errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + link + " in " + sourceFile.getName()));
                }
            }
        }
    }

    private void gatherDeadReferenceLinksInLine(Pattern pattern, @Nullable Reference reference, boolean markdown, File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        if (reference == null) {
            return;
        }
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String link = matcher.group(1).replace("++", "");
            File referencedFile = new File(reference.root, link);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                String errMsg = "Missing " + reference.name + " file for " + link + " in " + sourceFile.getName();
                if (link.startsWith(reference.root.getName() + "/")) {
                    errMsg += " (You may need to remove the leading `" + reference.root.getName() + "` path component)";
                }
                errorsForFile.add(new Error(lineNumber, line, errMsg));
            } else if (matcher.group(2) != null) {
                // A Markdown link target is closed by `)`, which is not part of the anchor
                String anchor = decodeAnchor(markdown ? beforeUnbalancedClosingParen(matcher.group(2)) : matcher.group(2));
                if (!anchorTargets(referencedFile).contains(anchor)) {
                    errorsForFile.add(new Error(lineNumber, line, "Missing anchor " + anchor + " in " + reference.name + " file " + link));
                }
            }
        }
    }

    private Reference javadoc() {
        return new Reference("Javadoc", getJavadocRoot().get().getAsFile());
    }

    @Nullable
    private Reference dsl() {
        return getDslRoot().isPresent() ? new Reference("DSL reference", getDslRoot().get().getAsFile()) : null;
    }

    /**
     * Resolves an anchor the way it reaches the browser, and the browser resolves it.
     */
    private String decodeAnchor(String anchor) {
        // Asciidoctor drops `++` passthrough markers
        String text = anchor.replace("++", "");
        // Character references such as `&#91;` stand in for characters a link macro target cannot contain
        text = numericCharacterReferencePattern.matcher(text).replaceAll(match -> Matcher.quoteReplacement(Character.toString(
            match.group(1) != null ? Integer.parseInt(match.group(1)) : Integer.parseInt(match.group(2), 16))));
        // Browsers percent-decode the fragment before looking up the target
        try {
            return URLDecoder.decode(text.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    private static String beforeUnbalancedClosingParen(String anchor) {
        int depth = 0;
        for (int i = 0; i < anchor.length(); i++) {
            char c = anchor.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth < 0) {
                return anchor.substring(0, i);
            }
        }
        return anchor;
    }

    private Set<String> anchorTargets(File file) {
        return anchorTargetsByFile.computeIfAbsent(file, f -> {
            Set<String> targets = new HashSet<>();
            try {
                Matcher matcher = anchorTargetPattern.matcher(Files.readString(f.toPath()));
                while (matcher.find()) {
                    targets.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            return targets;
        });
    }

    private boolean fileContainsText(File referencedFile, String text) {
        try {
            for (String line : Files.readAllLines(referencedFile.toPath())) {
                if (line.contains(text)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private String getFileName(String match, File currentFile) {
        if (match.isEmpty()) {
            return currentFile.getName();
        } else {
            if (match.endsWith(".adoc")) {
                return match;
            }
            return match + ".adoc";
        }
    }

    private static class Reference {
        private final String name;
        private final File root;

        private Reference(String name, File root) {
            this.name = name;
            this.root = root;
        }
    }

    private static class Error {
        private final int lineNumber;
        private final String line;
        private final String message;

        private Error(int lineNumber, String line, String message) {
            this.lineNumber = lineNumber;
            this.line = line;
            this.message = message;
        }
    }
}
