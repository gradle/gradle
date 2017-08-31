/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Builds a samples.xml from the available samples.
 */
@CacheableTask
class ExtractSamplesTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    File sourceFile

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File getUserguideSrc() {
        sourceFile?.parentFile
    }

    @OutputFile
    File destFile

    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(sourceFile)
        transformImpl(provider.document)
    }

    private def transformImpl(Document doc) {
        use(BuildableDOMCategory) {
            transformSamples(doc)
        }
    }

    def transformSamples(Document doc) {
        XIncludeAwareXmlProvider samplesXmlProvider = new XIncludeAwareXmlProvider()
        samplesXmlProvider.emptyDoc() << {
            samples()
        }
        Element samplesXml = samplesXmlProvider.root.documentElement
        doc.documentElement.depthFirst().findAll { it.name() == 'sample' }.each { Element element ->
            String sampleId = element.'@id'
            String srcDir = element.'@dir'
            SampleLayoutHandler layoutHandler = new SampleLayoutHandler(srcDir)

            samplesXml << { sample(id: sampleId, dir: srcDir) }

            element.children().each { Element child ->
                if (child.name() == 'output') {
                    String args = child.'@args'
                    String outputFile = child.'@outputFile' ?: "${sampleId}.out"
                    boolean ignoreExtraLines = child.'@ignoreExtraLines' ?: false
                    boolean ignoreLineOrder = child.'@ignoreLineOrder' ?: false
                    boolean expectFailure = child.'@expectFailure' ?: false

                    samplesXml << {
                        sample(id: sampleId, dir: srcDir, args: args, outputFile: outputFile,
                            ignoreExtraLines: ignoreExtraLines, ignoreLineOrder: ignoreLineOrder, expectFailure: expectFailure)
                    }
                } else if (child.name() == 'test') {
                    String args = child.'@args'
                    samplesXml << { sample(id: sampleId, dir: srcDir, args: args) }
                } else if (child.name() == 'layout') {
                    String args = child.'@after'
                    Element sampleElement = samplesXml << { sample(id: sampleId, dir: srcDir, args: args) }
                    layoutHandler.handleSample(child.text(), sampleElement)
                }
            }
        }

        samplesXmlProvider.write(destFile, true)
    }
}
