/*
 * Copyright 2015 the original author or authors.
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

package sample.markdown

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.pegdown.PegDownProcessor

class MarkdownHtmlCompile extends SourceTask {
    @OutputDirectory
    File destinationDir

    @Input
    boolean smartQuotes

    @Input
    boolean generateIndex

    @TaskAction
    void process() {
        def encoding = "UTF-8"

        PegDownProcessor processor = new PegDownProcessor(smartQuotes ? org.pegdown.Extensions.QUOTES : org.pegdown.Extensions.NONE)

        getSource().each { sourceFile ->
            String markdown = sourceFile.getText(encoding)
            String html = processor.markdownToHtml(markdown)
            File outputFile = new File(destinationDir, sourceFile.name.replace(".md", ".html"))
            outputFile.write(html, encoding)
        }
        if (generateIndex) {
            doGenerateIndex()
        }
    }

    def doGenerateIndex() {
        File indexFile = new File(destinationDir, "index.html")
        indexFile.withWriter { writer ->
            def markup = new groovy.xml.MarkupBuilder(writer)  // the builder
            markup.html{
                h1"Sample Userguide"
                h2"Content"
                ol {
                    getSource().each { sourceFile ->
                        def chapterTitle = sourceFile.name - ".md"
                        li {
                            a(href:chapterTitle + ".html", chapterTitle)
                        }
                    }
                }
            }
        }
    }
}
