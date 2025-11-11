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
 *
 * This task scans documentation, samples and release notes for various
 * internal link styles and reports missing targets (files or anchors).
 *
 * It intentionally treats some link styles as unsupported (for example,
 * Markdown-style links) because they don't render correctly in the single-page output.
 */
@CacheableTask
public abstract class FindBrokenInternalLinks extends DefaultTask {

    // Pattern that matches AsciiDoc xrefs like: <<file.adoc#section,Text>>
    private final Pattern linkPattern = Pattern.compile("<<([^,>]+)[^>]*>>");

    // Pattern to split a value like "file.adoc#section" into two groups:
    private final Pattern linkWithHashPattern = Pattern.compile("([a-zA-Z_0-9-.]*)#(.*)");

    // Pattern to detect Markdown-style links like: [text](https://...)
    private final Pattern markdownLinkPattern = Pattern.compile("\\[[^]]+]\\([^)^\\\\]+\\)");

    // Matches samples/... (filename part)
    private final Pattern releaseNotesSamplesPattern = Pattern.compile("samples/(.*?)(?=\\.html)");

    // ----- Task inputs / outputs ------------------------------------------------

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getDocumentationRoot();

    @Optional @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSamplesRoot();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getJavadocRoot();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getGroovyDslRoot();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getKotlinDslRoot();

    @Optional @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getReleaseNotesFile();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    // ----- Task action ---------------------------------------------------------

    @TaskAction
    public void checkDeadLinks() {
        Map<File, List<Error>> errors = new TreeMap<>();

        // First check release notes (if present)
        gatherDeadLinksInFileReleaseNotes(errors);

        // Then scan the samples tree (if provided).
        // Note: index.adoc files in samples are excluded intentionally.
        getSamplesRoot()
            .getAsFileTree()
            .matching(pattern -> {
                pattern.include("**/*.adoc");
                pattern.exclude("**/index.adoc"); // Exclude index.adoc files
            })
            .forEach(file -> gatherDeadLinksInFileSamples(file, errors));

        // Finally scan documentation root for *.adoc files
        getDocumentationRoot()
            .getAsFileTree()
            .matching(pattern -> pattern.include("**/*.adoc"))
            .forEach(file -> gatherDeadLinksInFileDocumentation(file, errors));

        // Write the report and fail the build if anything was found
        reportErrors(errors, getReportFile().get().getAsFile());
    }

    // ----- Reporting -----------------------------------------------------------

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
                // Log each file's aggregated errors to Gradle's logger and write to the report
                getLogger().error(message);
                fw.println(message);
            }
        } catch (IOException e) {
            // Wrap IO exceptions in Gradle's unchecked facility so the task fails sensibly
            throw UncheckedException.throwAsUncheckedException(e);
        }
        // If we got here there were errors — fail the build with a helpful message pointing to the report
        throw new GradleException("Documentation assertion failed: found invalid internal links. See " + new org.gradle.internal.logging.ConsoleRenderer().asClickableFileUrl(reportFile));
    }

    private static void writeHeader(PrintWriter fw) {
        // Short guidance for someone inspecting the generated report
        fw.println("# Valid links are:");
        fw.println("# * Inside the same file: <<(#)section-name(,text)>>");
        fw.println("# * To a different file: <<other-file(.adoc)#section-name,text>> - Note that the # and section are mandatory, otherwise the link is invalid in the single page output");
        fw.println("#");
        fw.println("# The checker does not handle implicit section names, so they must be explicit and declared as: [[section-name]]");
        fw.println("#");
        fw.println("# The checker also rejects Markdown-style links, such as [text](https://example.com/something) as they do not render properly");

    }

    // ----- Release notes scanning ----------------------------------------------

    private void gatherDeadLinksInFileReleaseNotes(Map<File, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();
        File sourceFile = getReleaseNotesFile().get().getAsFile();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                gatherDeadUserGuideLinksInLineReleaseNotes(line, lineNumber, errorsForFile);
                gatherDeadSamplesLinksInLineReleaseNotes(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadJavadocLinksInLineReleaseNotes(sourceFile, line, lineNumber, errorsForFile);
                gatherNonMarkdownLinksInLIneReleaseNotes(line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile, errorsForFile);
        }
    }

    // Release-notes: check user guide cross-references (userguide/upgrading_version_9.html)
    private void gatherDeadUserGuideLinksInLineReleaseNotes(String line, int lineNumber, List<Error> errorsForFile) {
        //  - userguide/upgrading_version_9.html                -> find upgrading_version_9.adoc
        //  - userguide/upgrading_version_9.html#changes_9.0.0  -> find anchor [[changes_9.0.0]] in upgrading_version_9.adoc
        Pattern p = Pattern.compile(
            "userguide/" +                      // literal prefix
            "([^#\\[\\s]+\\.html)" +            // group(1): filename with .html (no #, [, or whitespace)
            "(?:#([^\\]\\)\\s\\.,;!\\?]+))?" +  // optional group(2): anchor (stop at ], ), whitespace or common punctuation; colon is now allowed
            "(?:\\[[^\\]]*\\])?"                // optional bracketed label that may follow
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlName = matcher.group(1);           // e.g. "upgrading_version_9.html"
            String anchor = matcher.group(2);             // e.g. "changes_9.0.0" or null if absent
            String adocName = htmlName.endsWith(".html") ? htmlName.replace(".html", ".adoc") : htmlName + ".adoc";
            File referencedFile = new File(getDocumentationRoot().get().getAsFile(), adocName);
            if (!referencedFile.exists()) {
                // Missing .adoc file entirely
                errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + adocName));
            } else {
                if (anchor != null && !anchor.isEmpty()) {
                    // Ignore anchors that include @baseVersion@
                    // Verify the target .adoc contains the named section [[anchor]]
                    if (!anchor.contains("@baseVersion@") && fileDoesNotContainText(referencedFile, "[[" + anchor + "]]")) {
                        errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + anchor + " in " + adocName));
                    }
                }
            }
        }
    }

    // Release-notes: check samples references (sample/sample_building_java_applications.html)
    private void gatherDeadSamplesLinksInLineReleaseNotes(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        //  - sample/sample_building_java_applications.html     -> find sample_building_java_applications.adoc
        Matcher matcher = releaseNotesSamplesPattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1);
            String fileName = getFileName(link, sourceFile);
            File referencedFile = new File(getSamplesRoot().get().getAsFile(), fileName);
            if (!referencedFile.exists()) {
                // Missing .adoc file entirely
                errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + fileName));
            }
        }
    }

    // Release-notes: check javadoc links referenced in the release notes (javadoc/org/gradle/testkit/runner/BuildResult.html#getOutput())
    private void gatherDeadJavadocLinksInLineReleaseNotes(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        //  - javadoc/org/gradle/api/attributes/AttributeContainer.html#named(java.lang.Class,java.lang.String) -> find AttributeContainer.html
        //  - javadoc/org/gradle/testkit/runner/BuildResult.html#getOutput()    -> find href of anchor getOutput() as href="#named(java.lang.Class,java.lang.String)" in AttributeContainer.html
        Pattern p = Pattern.compile(
            "javadoc/" +                                            // literal prefix
            "([^#\\s\\[]+\\.html)" +                                // group(1): HTML file path
            "(?:#([A-Za-z0-9_.$]+\\([^)]*\\)|[A-Za-z0-9_.$]+))?"    // group(2): anchor (method() OR field)
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlPath = matcher.group(1);   // e.g. "org/gradle/api/.../SomeClass.html"
            String anchor = matcher.group(2);     // e.g. "named(java.lang.Class,java.lang.String)" or null
            File referencedFile = new File(getJavadocRoot().get().getAsFile(), htmlPath);
            // Missing .html file entirely
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                errorsForFile.add(new Error(lineNumber, line, "Missing Javadoc file for " + htmlPath + " in " + sourceFile.getName()));
            } else {
                if (anchor != null && !anchor.isEmpty()) {
                    String hrefSearch = "href=\"#" + anchor + "\"";
                    if (fileDoesNotContainText(referencedFile, hrefSearch)) {
                        // Verify the anchor exits as href in .html
                        errorsForFile.add(new Error(lineNumber, line, "Missing Javadoc href fragment '#" + anchor + "' in " + referencedFile.getName()));
                    }
                }
            }
        }
    }

    // Release-notes: ensure release notes use Markdown-style links and not other link types (no link:[])
    private static void gatherNonMarkdownLinksInLIneReleaseNotes(String line, int lineNumber, List<Error> errorsForFile) {
        // Robust detection for ANY "link:" usage (http(s), relative, variable, etc).
        Pattern linkAnyPattern = Pattern.compile("(?i)(?:\\blink:|\\slink:|^link:)(\\S+)");
        Matcher matcher = linkAnyPattern.matcher(line);
        while (matcher.find()) {
            String msg = "Use Markdown-style links in release notes (e.g. [text](url)); found link: instead";
            errorsForFile.add(new Error(lineNumber, line, msg));
        }
    }

    // ----- Samples scanning ---------------------------------------------------

    private void gatherDeadLinksInFileSamples(File sourceFile, Map<File, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                gatherDeadUserGuideLinksInLineSamples(line, lineNumber, errorsForFile);
                gatherDeadJavadocLinksInLineSamples(sourceFile, line, lineNumber, errorsForFile);
                gatherMarkdownLinksInLineSamples(line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile, errorsForFile);
        }
    }

    // Samples: check user-guide links embedded in samples (link:{userManualPath}/...)
    private void gatherDeadUserGuideLinksInLineSamples(String line, int lineNumber, List<Error> errorsForFile) {
        // link:{userManualPath}/gradle_ides.html#gradle_ides
        Pattern p = Pattern.compile(
            "link:\\{userManualPath}/" +        // literal prefix
                "([^#\\[\\s]+)" +               // group(1): filename (no #, [, or whitespace)
                "(?:#([^\\]\\s]+))?" +          // optional group(2): anchor (no ] or whitespace)
                "\\[[^\\]]*\\]"                 // the bracketed label that follows
        );
        Matcher matcher = p.matcher(line); // pattern that finds gradle_ides.html
        while (matcher.find()) {
            String htmlName = matcher.group(1); // e.g. "gradle_ides.html"
            String anchor = matcher.group(2);   // e.g. "gradle_ides" or null if absent
            String adocName = htmlName.endsWith(".html") ? htmlName.replace(".html", ".adoc") : htmlName + ".adoc";
            File referencedFile = new File(getDocumentationRoot().get().getAsFile(), adocName);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                // Missing .adoc file entirely
                errorsForFile.add(new Error(lineNumber, line, "Looking for file named " + adocName));
            } else {
                if (anchor != null && !anchor.isEmpty()) {
                    // Verify the target .adoc contains the named section [[anchor]]
                    if (fileDoesNotContainText(referencedFile, "[[" + anchor + "]]")) {
                        errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + anchor + " in " + referencedFile));
                    }
                }
            }
        }
    }

    // Samples: check javadoc links embedded in samples (link:{javadocPath}/.../SomeClass.html)
    private void gatherDeadJavadocLinksInLineSamples(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        // link:{javadocPath}/.../SomeClass.html#method()
        Pattern p = Pattern.compile(
            "link:\\{javadocPath\\}/" + // literal prefix
            "([^#\\s\\[]+)" +           // group(1): the HTML filename/path (no #, whitespace or '[')
            "(?:#([^\\s\\[]+))?"        // optional group(2): the anchor/fragment (no whitespace or '[')
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlPath = matcher.group(1);   // e.g. "org/gradle/api/.../SomeClass.html"
            String anchor = matcher.group(2);     // e.g. "named(java.lang.Class,java.lang.String)" or null
            File referencedFile = new File(getJavadocRoot().get().getAsFile(), htmlPath);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                errorsForFile.add(new Error(lineNumber, line, "Missing Javadoc file for " + htmlPath + " in " + sourceFile.getName()));
            } else {
                if (anchor != null && !anchor.isEmpty()) {
                    String hrefSearch = "href=\"#" + anchor + "\"";
                    if (fileDoesNotContainText(referencedFile, hrefSearch)) {
                        // Verify the anchor exits as href in .html
                        errorsForFile.add(new Error(lineNumber, line, "Missing Javadoc href fragment '#" + anchor + "' in " + referencedFile.getName()));
                    }
                }
            }
        }
    }

    // Samples: ensure samples use AsciiDoc-style links and not other link types. (no [name](url) or <<name#anchor>>)
    private void gatherMarkdownLinksInLineSamples(String line, int lineNumber, List<Error> errorsForFile) {
        Matcher mdMatcher = markdownLinkPattern.matcher(line);
        while (mdMatcher.find()) {
            String invalidLink = mdMatcher.group();
            errorsForFile.add(new Error(lineNumber, line, "Markdown-style links are not supported: " + invalidLink));
        }
        Matcher xrefMatcher = linkPattern.matcher(line);
        while (xrefMatcher.find()) {
            String xrefTarget = xrefMatcher.group(1);
            if (xrefTarget != null && xrefTarget.contains("#")) {
                String reported = "<<" + xrefTarget + ">>";
                errorsForFile.add(new Error(lineNumber, line, "AsciiDoc xrefs with anchors are not supported in samples: " + reported));
            }
        }
    }

    // ----- Documentation scanning ---------------------------------------------

    private void gatherDeadLinksInFileDocumentation(File sourceFile, Map<File, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                gatherDeadSamplesLinksInLineDocumentation(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadJavadocLinksInLineDocumentation(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadKotlinDslLinksInLineDocumentation(line, lineNumber, errorsForFile);
                gatherDeadGroovyDslLinksInLineDocumentation(sourceFile, line, lineNumber, errorsForFile);
                gatherDeadLinksInLineDocumentation(sourceFile, line, lineNumber, errorsForFile);
                gatherMarkdownLinksInLineDocumentation(line, lineNumber, errorsForFile);

                line = br.readLine();
            }
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile, errorsForFile);
        }
    }

    // Documentation: handle samples (link:../samples/index.html#groovy[Groovy])
    private void gatherDeadSamplesLinksInLineDocumentation(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Pattern p = Pattern.compile(
            "link:../samples/" +            // literal prefix
                "([^#\\s\\[]+\\.html)" +    // group(1): filename with .html (no #, whitespace or '[')
                "(?:#([^\\[\\]\\s]+))?" +   // optional group(2): anchor (no '[' or ']' or whitespace)
                "(?:\\[[^\\]]*\\])?"        // optional bracketed label that may follow
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlName = matcher.group(1); // e.g. "index.html"
            String anchor = matcher.group(2);   // e.g. "groovy" or null
            String adocName = htmlName.endsWith(".html") ? htmlName.replace(".html", ".adoc") : htmlName + ".adoc";
            File referencedFile = new File(getSamplesRoot().get().getAsFile(), adocName);
            // Check that the target .adoc exists
            if (!referencedFile.getName().equals("index.adoc")) {
                if(!referencedFile.exists() || referencedFile.isDirectory()) {
                    errorsForFile.add(new Error(lineNumber, line, "Missing Samples file for " + adocName + " in " + sourceFile.getName()));
                } else {
                    if (anchor != null && !anchor.isEmpty()) {
                        // If an anchor was present, verify the .adoc contains the named section [[anchor]]
                        if (fileDoesNotContainText(referencedFile, "[[" + anchor + "]]")) {
                            errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + anchor + " in " + adocName));
                        }
                    }
                }
            }
        }
    }

    // Documentation: check javadoc links (link:{javadocPath}/.../SomeClass.html#method()[])
    private void gatherDeadJavadocLinksInLineDocumentation(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Pattern p = Pattern.compile(
            "link:\\{javadocPath\\}/" +         // literal prefix
            "([^#\\s\\[]+\\.html)" +            // group(1): HTML file path (no #, whitespace or '[')
            "(?:#([^\\[\\s]+))?"                // group(2): optional anchor = everything up to '[' or whitespace
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlPath = matcher.group(1);   // e.g. "org/gradle/api/.../SomeClass.html"
            String anchor = matcher.group(2);     // e.g. "from-java.lang.Object-" or "getOutput()" or null
            File referencedFile = new File(getJavadocRoot().get().getAsFile(), htmlPath);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                errorsForFile.add(new Error(lineNumber, line, "Missing Javadoc file for " + htmlPath + " in " + sourceFile.getName()));
            } else {
                if (anchor != null && !anchor.isEmpty()) {
                    String hrefSearch = "href=\"#" + anchor + "\"";
                    if (fileDoesNotContainText(referencedFile, hrefSearch)) {
                        errorsForFile.add(new Error(lineNumber, line, "Missing Javadoc href fragment '#" + anchor + "' in " + referencedFile.getName()));
                    }
                }
            }
        }
    }

    // Documentation: check kotlin dsl links (link:{kotlinDslPath}/gradle/.../index.html[register()])
    private void gatherDeadKotlinDslLinksInLineDocumentation(String line, int lineNumber, List<Error> errorsForFile) {
        Pattern p = Pattern.compile(
            "link:\\{kotlinDslPath\\}/" +            // literal prefix
                "([^#\\s\\[]+\\.html)" +             // group(1): HTML filename/path (with .html)
                "(?:#([^\\[\\]\\s]+))?" +            // optional group(2): fragment (stop at '[' or ']' or whitespace)
                "(?:\\[[^\\]]*\\])?"                 // optional bracketed label that may follow
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlPath = matcher.group(1);      // e.g. "gradle/org.gradle.kotlin.dsl/configure.html"
            File referencedFile = new File(getKotlinDslRoot().get().getAsFile(), htmlPath);
            if (!referencedFile.exists() || referencedFile.isDirectory()) {
                // Check html file exists in
                errorsForFile.add(new Error(lineNumber, line, "Missing KOTLIN DSL HTML file for " + htmlPath + " (expected " + htmlPath + ")"));
            }
        }
    }

    // Documentation: check groovy dsl links (link:{groovyDslPath}/org.gradle.api.plugins.quality.Checkstyle.html[Checkstyle])
    private void gatherDeadGroovyDslLinksInLineDocumentation(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Pattern p = Pattern.compile(
            "link:\\{groovyDslPath\\}/" +            // literal prefix
                "([^#\\s\\[]+\\.html)" +             // group(1): HTML filename/path (with .html)
                "(?:#([^\\[\\]\\s]+))?" +            // optional group(2): fragment (stop at '[' or ']' or whitespace)
                "(?:\\[[^\\]]*\\])?"                 // optional bracketed label that may follow
        );
        Matcher matcher = p.matcher(line);
        while (matcher.find()) {
            String htmlPath = matcher.group(1);   // e.g. "org.gradle.api.plugins.quality.Checkstyle.html"
            String fragment = matcher.group(2);   // e.g. "org.gradle.api.plugins.quality.Checkstyle:maxHeapSize" or null
            String xmlName = htmlPath.endsWith(".html") ? htmlPath.replace(".html", ".xml") : htmlPath + ".xml";
            File referencedFile = new File(getGroovyDslRoot().get().getAsFile(), xmlName);
            if (!referencedFile.getName().equals("index.xml")) {
                if (!referencedFile.exists() || referencedFile.isDirectory()) {
                    // Check xml file exists in
                    errorsForFile.add(new Error(lineNumber, line, "Missing GROOVY DSL XML file for " + htmlPath + " (expected " + xmlName + ")"));
                }
            }
        }
    }

    // Documentation: core AsciiDoc xref handling: <<file.adoc#section,text>> or <<#section,text>> (internal)
    private void gatherDeadLinksInLineDocumentation(File sourceFile, String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = linkPattern.matcher(line);
        while (matcher.find()) {
            MatchResult xrefMatcher = matcher.toMatchResult();
            String link = xrefMatcher.group(1);
            if (link.contains("#")) {
                // Split file#section
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
                            // Anchor missing after '#'
                            errorsForFile.add(new Error(lineNumber, line, "Missing section reference for link to " + fileName));
                        } else {
                            // Verify the referenced file contains a named section [[idName]]
                            if (fileDoesNotContainText(referencedFile, "[[" + idName + "]]")) {
                                errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + idName + " in " + fileName));
                            }
                        }
                    }
                }
            } else {
                // local xref to a section in the same file: check for [[section-name]] in the current file
                if (fileDoesNotContainText(sourceFile, "[[" + link + "]]")) {
                    errorsForFile.add(new Error(lineNumber, line, "Looking for section named " + link + " in " + sourceFile.getName()));
                }
            }
        }
    }

    // Documentation: detect Markdown-style links and mark them as errors (unsupported)
    private void gatherMarkdownLinksInLineDocumentation(String line, int lineNumber, List<Error> errorsForFile) {
        Matcher matcher = markdownLinkPattern.matcher(line);
        while (matcher.find()) {
            String invalidLink = matcher.group();
            errorsForFile.add(new Error(lineNumber, line, "Markdown-style links are not supported: " + invalidLink));
        }
    }

    // ----- Helpers ------------------------------------------------------------

    /**
     * Reads the whole file and checks if any line contains the given text.
     * Used to validate that a referenced anchor (e.g. [[idName]]) exists.
     */
    private static boolean fileDoesNotContainText(File referencedFile, String text) {
        try {
            for (String line : Files.readAllLines(referencedFile.toPath())) {
                if (line.contains(text)) {
                    return false;
                }
            }
        } catch (IOException e) {
            // If we can't read the file, treat it as not containing the text (and the caller will report a missing target)
        }
        return true;
    }

    /**
     * Normalizes a matched value into an .adoc filename.
     * If the match is empty, returns the current file's name (i.e., an internal xref).
     * If the match already has .adoc, return as-is; otherwise append .adoc.
     */
    private static String getFileName(String match, File currentFile) {
        if (match.isEmpty()) {
            return currentFile.getName();
        } else {
            if (match.endsWith(".adoc")) {
                return match;
            }
            return match + ".adoc";
        }
    }

    /**
     * Small immutable holder for error details gathered while scanning files
     */
    private record Error(int lineNumber, String line, String message) {}
}
