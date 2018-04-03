/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable

import java.util.regex.Matcher
import java.util.regex.Pattern
/**
 * Produces the snippets files for a set of sample source files.
 */
@CacheableTask
class ExtractSnippetsTask extends DefaultTask {

    /**
     * Output directory for samples
     */
    @OutputDirectory
    File destDir

    /**
     * Output directory for extracted snippets
     */
    @OutputDirectory
    File snippetsDir

    /**
     * Patterns for resources that should not be filtered
     */
    @Input
    List<String> nonFiltered

    /**
     * Source directory of the samples
     */
    @Internal
    File samples

    /**
     * Files to exclude from the samples
     */
    @Internal
    List<String> excludes

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        def source = project.fileTree(samples)
        source.excludes = excludes
        return source
    }

    @TaskAction
    def extract() {
        project.copy { copySpec ->
            copySpec.from samples
            copySpec.into destDir
            copySpec.setIncludes(nonFiltered)
        }

        getSource().matching(canBeFiltered()).visit {
            filterSample(it)
        }
    }

    private Closure canBeFiltered() {
        return { PatternFilterable patternFilterable ->
            patternFilterable.exclude(nonFiltered)
        }
    }

    void filterSample(FileVisitDetails details) {
        String name = details.relativePath.pathString
        if (details.file.isDirectory()) {
            File destDir = new File(destDir, name)
            destDir.mkdirs()
            destDir = new File(snippetsDir, name)
            destDir.mkdirs()
        } else {
            File srcFile = details.file
            File destFile = new File(destDir, name)

            destFile.parentFile.mkdirs()

            if (['.war', '.jar', '.zip', '.gpg'].find{ name.endsWith(it) }) {
                destFile.withOutputStream { it.write(srcFile.readBytes()) }
                return
            }

            Map writers = [
                0: new SnippetWriter(name, destFile).start(),
                1: new SnippetWriter(name, new File(snippetsDir, name)).start()
            ]
            Pattern startSnippetPattern
            Pattern endSnippetPattern
            if (name.endsWith('.xml')) {
                startSnippetPattern = Pattern.compile('\\s*<!--\\s*START\\s+SNIPPET\\s+(\\S+)\\s*-->')
                endSnippetPattern = Pattern.compile('\\s*<!--\\s*END\\s+SNIPPET\\s+(\\S+)\\s*-->')
            } else {
                startSnippetPattern = Pattern.compile('.*START\\s+SNIPPET\\s+(\\S+)\\s*')
                endSnippetPattern = Pattern.compile('.*END\\s+SNIPPET\\s+(\\S+)\\s*')
            }

            try {
                // Can't use eachLine {} because it throws away blank lines
                srcFile.withReader {Reader r ->
                    BufferedReader reader = new BufferedReader(r)
                    reader.readLines().each {String line ->
                        Matcher m = startSnippetPattern.matcher(line)
                        if (m.matches()) {
                            String snippetName = m.group(1)
                            if (!writers[snippetName]) {
                                File snippetFile = new File(snippetsDir, "$name-$snippetName")
                                writers.put(snippetName, new SnippetWriter("Snippet $snippetName in $name", snippetFile))
                            }
                            writers[snippetName].start()
                            return
                        }
                        m = endSnippetPattern.matcher(line)
                        if (m.matches()) {
                            String snippetName = m.group(1)
                            writers[snippetName].end()
                            return
                        }
                        writers.values().each {SnippetWriter w ->
                            w.println(line)
                        }
                    }
                }
            } finally {
                writers.values().each {SnippetWriter w ->
                    w.close()
                }
            }
        }
    }
}
