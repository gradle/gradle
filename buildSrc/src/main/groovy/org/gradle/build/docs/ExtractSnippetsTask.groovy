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

import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

import java.util.regex.Matcher
import java.util.regex.Pattern
/**
 * Produces the snippets files for a set of sample source files.
 */
@CacheableTask
class ExtractSnippetsTask extends SourceTask {

    @OutputDirectory
    File destDir

    @OutputDirectory
    File snippetsDir

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource()
    }

    @TaskAction
    def extract() {
        source.visit { FileVisitDetails details ->
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
                    startSnippetPattern = Pattern.compile('\\s*//\\s*START\\s+SNIPPET\\s+(\\S+)\\s*')
                    endSnippetPattern = Pattern.compile('\\s*//\\s*END\\s+SNIPPET\\s+(\\S+)\\s*')
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
}
