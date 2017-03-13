/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

import static ProviderBasedProjectUnderTest.Language
import static ProviderBasedProjectUnderTest.OUTPUT_FILE_CONTENT
import static org.gradle.util.TextUtil.normaliseFileSeparators

class PropertyStateIntegrationTest extends AbstractIntegrationSpec {

    private final ProviderBasedProjectUnderTest projectUnderTest = new ProviderBasedProjectUnderTest(testDirectory)

    @Unroll
    def "can create and use property state by custom task written as #language class"() {
        given:
        projectUnderTest.writeCustomTaskTypeToBuildSrc(language)
        buildFile << """
            task myTask(type: MyTask)
        """

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()

        when:
        buildFile << """
             myTask {
                enabled = true
                outputFiles = files("${normaliseFileSeparators(projectUnderTest.customOutputFile.canonicalPath)}")
            }
        """
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()

        where:
        language << [Language.GROOVY, Language.JAVA]
    }

    def "can lazily map extension property state to task property with convention mapping"() {
        given:
        projectUnderTest.writeCustomGroovyBasedTaskTypeToBuildSrc()
        projectUnderTest.writePluginWithExtensionMappingUsingConventionMapping()

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }

    def "can lazily map extension property state to task property with property state"() {
        given:
        projectUnderTest.writeCustomGroovyBasedTaskTypeToBuildSrc()
        projectUnderTest.writePluginWithExtensionMappingUsingPropertyState()

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }

    def "can use property state type to infer task dependency"() {
        given:
        buildFile << """
            task producer(type: Producer) {
                text = '$OUTPUT_FILE_CONTENT'
                outputFiles = files("\$buildDir/helloWorld.txt")
            }

            task consumer(type: Consumer) {
                inputFiles = producer.outputs.files
            }

            class Producer extends DefaultTask {
                @Input
                String text

                private final PropertyState<ConfigurableFileCollection> outputFiles = project.property(ConfigurableFileCollection)
                
                void setOutputFiles(ConfigurableFileCollection outputFiles) {
                    this.outputFiles.set(outputFiles)
                }
                
                @OutputFiles
                ConfigurableFileCollection getOutputFiles() {
                    outputFiles.get()
                }

                @TaskAction
                void produce() {
                    getOutputFiles().each {
                        it << text
                    }
                }
            }
            
            class Consumer extends DefaultTask {
                @InputFiles
                FileCollection inputFiles
                
                @TaskAction
                void consume() {
                    inputFiles.each {
                        println it.text
                    }
                }
            }
        """

        when:
        succeeds('consumer')

        then:
        executedTasks.containsAll(':producer', ':consumer')
        outputContains(OUTPUT_FILE_CONTENT)
    }
}
