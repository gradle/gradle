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

import groovy.xml.DOMBuilder
import groovy.xml.XmlUtil
import groovy.xml.dom.DOMCategory
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.*
import org.w3c.dom.Document
import org.w3c.dom.Element

import javax.xml.parsers.DocumentBuilderFactory

/**
 * Generates a chapter containing a summary of the readme files for the samples.
 */
@CacheableTask
class AssembleSamplesDocTask extends SourceTask {

    @OutputFile
    File destFile

    @Override
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getSource() {
        return super.getSource()
    }

    @TaskAction
    def generate() {
        List samples = []
        use(DOMCategory) {

            // Collect up the source sample.xml files
            source.visit { FileVisitDetails fvd ->
                if (fvd.isDirectory()) {
                    return
                }

                Element sourceDoc
                fvd.file.withReader { Reader reader ->
                    sourceDoc = DOMBuilder.parse(reader).documentElement
                }

                Element firstPara = sourceDoc.para[0]
                if (!firstPara) {
                    throw new RuntimeException("Source file $fvd.file does not contain any <para> elements.")
                }
                samples << [
                    dir      : fvd.relativePath.parent as String,
                    firstPara: firstPara,
                    doc      : sourceDoc,
                    include  : sourceDoc['*'].size() > 1
                ]
            }

            samples = samples.sort { it.dir }

            def doc = newDocument()
            def builder = new DomBuilder(doc)
            builder.appendix(id: 'sample_list') {
                title('Gradle Samples')
                para {
                    text('Listed below are some of the stand-alone samples which are included in the Gradle distribution. ')
                    text('You can find these samples in the ')
                    filename {
                        replaceable('GRADLE_HOME')
                        text('/samples')
                    }
                    text(' directory of the distribution.')
                }
                table {
                    title('Samples included in the distribution')
                    thead {
                        td('Sample')
                        td('Description')
                    }
                    samples.each { sample ->
                        tr {
                            td {
                                if (sample.include) {
                                    link(linkend: sample.hashCode()) { filename(sample.dir) }
                                } else {
                                    filename(sample.dir)
                                }
                            }
                            td(sample.firstPara)
                        }
                    }
                }
                samples.each { sample ->
                    if (!sample.include) {
                        return
                    }
                    section(id: sample.hashCode()) {
                        title {
                            text('Sample ')
                            filename(sample.dir)
                        }
                        sample.doc.childNodes.each { n ->
                            appendChild n
                        }
                    }
                }
            }

            destFile.withOutputStream { OutputStream stream ->
                XmlUtil.serialize(doc.documentElement, stream)
            }
        }
    }

    Document newDocument() {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    }
}
