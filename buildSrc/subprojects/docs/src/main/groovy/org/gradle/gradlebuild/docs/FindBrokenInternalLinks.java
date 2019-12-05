/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Checks adoc files for broken links.
 *
 * TODO: This is currently broken.
 */
@CacheableTask
public abstract class FindBrokenInternalLinks extends DefaultTask {
    private final Pattern linkPattern = Pattern.compile("<<([^,>]+)[^>]*>>");
    private final Pattern linkWithHashPattern = Pattern.compile("([a-zA-Z_0-9-.]*)#(.*)");

    @Internal
    public abstract DirectoryProperty getDocumentationRoot();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDocumentationFiles();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void checkDeadLinks() {
        Map<String, List<Error>> errors = new TreeMap<>();
        File documentationRoot = getDocumentationRoot().get().getAsFile();

        getDocumentationFiles().forEach(file -> {
            hasDeadLink(documentationRoot, file, errors);
        });
        reportErrors(errors, getReportFile().get().getAsFile());
    }

    private void reportErrors(Map<String, List<Error>> errors, File reportFile) {
        try (PrintWriter fw = new PrintWriter(new FileWriter(reportFile))) {
            writeHeader(fw);
            if (errors.isEmpty()) {
                fw.println("All clear!");
                return;
            }
            for (Map.Entry<String, List<Error>> e : errors.entrySet()) {
                String file = e.getKey();
                List<Error> errorsForFile = e.getValue();
                fw.println("In " + file);
                for (Error error : errorsForFile) {
                    fw.println("   - At line " + error.line + "invalid include " + error.missingFile);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new GradleException("Documentation check failed: found invalid internal links. See " + new org.gradle.internal.logging.ConsoleRenderer().asClickableFileUrl(reportFile));
    }

    private void writeHeader(PrintWriter fw) {
        fw.println("# Valid links are:");
        fw.println("# * Inside the same file: <<(#)section-name(,text)>>");
        fw.println("# * To a different file: <<other-file(.adoc)#(section-name),text>> - Note that the # is mandatory, otherwise the link is considered to be inside the file!");
        fw.println("#");
        fw.println("# The checker does not handle implicit section names, so they must be explicit and declared as: [[section-name]]");
    }

    private void hasDeadLink(File baseDir, File sourceFile, Map<String, List<Error>> errors) {
        int lineNumber = 0;
        List<Error> errorsForFile = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(sourceFile))) {
            String line = br.readLine();
            while (line != null) {
                lineNumber++;
                // TODO: fix this
//                linkPattern.findAll(line).forEach {
//                    val link = it.groupValues[1]
//                    if (link.contains('#')) {
//                        linkWithHashPattern.find(link) !!.apply {
//                            val fileName = getFileName(this.groupValues[1], sourceFile)
//                            val referencedFile = File(baseDir, fileName)
//                            if (!referencedFile.exists() || referencedFile.isDirectory) {
//                                errorsForFile.add(Error(lineNumber, fileName))
//                            } else {
//                                val idName = this.groupValues[2]
//                                if (idName.isNotEmpty()) {
//                                    if (!referencedFile.readText().contains("[[$idName]]")) {
//                                        errorsForFile.add(Error(lineNumber, "$fileName $idName"))
//                                    }
//                                }
//                            }
//                        }
//                    } else {
//                        if (!sourceFile.readText().contains("[[$link]]")) {
//                            errorsForFile.add(Error(lineNumber, "${sourceFile.name} $link"))
//                        }
//                    }
//                }
                line = br.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!errorsForFile.isEmpty()) {
            errors.put(sourceFile.getAbsolutePath(), errorsForFile);
        }
    }
//
//    private String getFileName(String match, File currentFile) {
//        return if (match.isNotEmpty()) {
//            if (match.endsWith(".adoc")) match else "$match.adoc"
//        } else {
//            currentFile.name
//        }
//    }

    private static class Error {
        private final int line;
        private final String missingFile;

        private Error(int line, String missingFile) {
            this.line = line;
            this.missingFile = missingFile;
        }
    }
}
