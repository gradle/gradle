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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    // link:{javadocPath}/org/gradle/api/java/archives/ManifestMergeDetails.html[ManifestMergeDetails]
    private final Pattern javadocLinkPattern = Pattern.compile("link:\\{javadocPath\\}/(.*?\\.html)");
    // link:../samples/sample_problems_api_usage.html[end-to-end sample]
    private final Pattern samplesLinkPattern = Pattern.compile("link:../samples/(.*?\\.html)");
    // link:https://kotlinlang.org/docs/reference/using-gradle.html#targeting-the-jvm[Kotlin]
    private final Pattern markdownLinkPattern = Pattern.compile("\\[[^]]+]\\([^)^\\\\]+\\)");

    // <a href="javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html">
    private final Pattern releaseNotesJavadocPattern = Pattern.compile("javadoc/(.*?\\.html)");
    // <a href="userguide/upgrading_version_8.html#changes_@baseVersion@">
    private final Pattern releaseNotesUserGuidePattern = Pattern.compile("userguide/(.*?)(?=\\.html)");
    // <a href="samples/sample_problems_api_usage.html">
    private final Pattern releaseNotesSamplesPattern = Pattern.compile("samples/(.*?)(?=\\.html)");

    // link:{userManualPath}/gradle_ides.html#gradle_ides[IDE that supports Gradle]
    private final Pattern samplesUserGuidePattern = Pattern.compile("link:\\{userManualPath\\}/(.*?\\.html)");
    // <<sample_build_android_apps.adoc,Sample>>
    private final Pattern samplesLinkWithHashPattern = Pattern.compile("([a-zA-Z_0-9-.]*)(#(.*))?");

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocumentationRoot();

    @Optional @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSamplesRoot();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getJavadocRoot();

    @Optional @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getReleaseNotesFile();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void checkDeadLinks() {
        Map<File, List<Error>> errors = new TreeMap<>();

        gatherDeadLinksInFileReleaseNotes(errors);

        getSamplesRoot()
            .getAsFileTree()
            .matching(pattern -> {
                pattern.include("**/*.adoc");
                pattern.exclude("**/index.adoc"); // Exclude index.adoc files
            })
            .forEach(file -> gatherDeadLinksInFileSamples(file, errors));

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
            throw new UncheckedIOException(e);
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
                gatherDeadSamplesLinksInLineReleaseNotes(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadJavadocLinksInLineReleaseNotes(sourceFile, line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private void gatherDeadSamplesLinksInLineReleaseNotes(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = releaseNotesSamplesPattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1);
            String fileName = getFileName(link, sourceFile);
            File referencedFile = new File(getSamplesRoot().get().getAsFile(), fileName);
            if (!referencedFile.exists()) {
                errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + fileName));
            }
        }
    }

    private void gatherDeadJavadocLinksInLineReleaseNotes(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = releaseNotesJavadocPattern.matcher(line);
        while (matcher.find()) {
            MatchResult linkMatcher = matcher.toMatchResult();
            String link = linkMatcher.group(1);
            File referencedFile = new File(getJavadocRoot().get().getAsFile(), link);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                String errMsg = "Missing Javadoc file for " + link + " in " + sourceFile.getName();
                if (link.startsWith("javadoc")) {
                    errMsg += " (You may need to remove the leading `javadoc` path component)";
                }
                errorsForFile.add(new Error(lineNumber, line, errMsg));
            }
            // TODO: Also parse the HTML in the javadoc file to check if the specific method is present
        }
    }

    private void gatherDeadLinksInFileSamples(File sourceFile, Map<File, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                gatherDeadLinksInLineSamples(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadJavadocLinksInLine(sourceFile, line, lineNumber, errorsForFile);
                gatherMarkdownLinksInLine(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadSamplesLinksInLineSamples(sourceFile, line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile, errorsForFile);
        }
    }

    private void gatherDeadLinksInLineSamples(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = samplesUserGuidePattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1).replace(".html", ".adoc");
            File referencedFile = new File(getDocumentationRoot().get().getAsFile(), link);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + link));
            }
        }
    }

    private void gatherDeadSamplesLinksInLineSamples(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = linkPattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1);
            Matcher linkMatcher = samplesLinkWithHashPattern.matcher(link);
            if (linkMatcher.matches()) {
                MatchResult result = linkMatcher.toMatchResult();
                String fileName = getFileName(result.group(1), sourceFile);
                File referencedFile = new File(getSamplesRoot().get().getAsFile(), fileName);
                if (!referencedFile.exists() || referencedFile.isDirectory()) {
                    errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + fileName));
                }
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
                gatherDeadSamplesLinksInLine(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadJavadocLinksInLine(sourceFile, line, lineNumber, errorsForFile);
                gatherMarkdownLinksInLine(sourceFile, line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private void gatherDeadSamplesLinksInLine(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = samplesLinkPattern.matcher(line);
        while (matcher.find()) {
            MatchResult linkMatcher = matcher.toMatchResult();
            String link = linkMatcher.group(1).replace(".html", ".adoc");
            File referencedFile = new File(getSamplesRoot().get().getAsFile(), link);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                String errMsg = "Missing Samples file for " + link + " in " + sourceFile.getName();
                errorsForFile.add(new Error(lineNumber, line, errMsg));
            }
            // TODO: Also parse the HTML in the javadoc file to check if the specific method is present
        }
    }

    private void gatherDeadJavadocLinksInLine(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = javadocLinkPattern.matcher(line);
        while (matcher.find()) {
            MatchResult linkMatcher = matcher.toMatchResult();
            String link = linkMatcher.group(1);
            File referencedFile = new File(getJavadocRoot().get().getAsFile(), link);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                String errMsg = "Missing Javadoc file for " + link + " in " + sourceFile.getName();
                if (link.startsWith("javadoc")) {
                    errMsg += " (You may need to remove the leading `javadoc` path component)";
                }
                errorsForFile.add(new Error(lineNumber, line, errMsg));
            }
            // TODO: Also parse the HTML in the javadoc file to check if the specific method is present
        }
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
