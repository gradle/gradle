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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class ArtifactTransformWithFileInputsIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {
    /**
     * Caller should add define an 'inputFiles' property to define the inputs to the transform
     */
    def setupBuildWithTransformFileInputs() {
        setupBuildWithColorTransform {
            params("""
                someFiles.from { project.inputFiles }
            """)
        }
        buildFile << """
            @TransformAction(MakeGreenAction)
            interface MakeGreen {
                @Input
                ConfigurableFileCollection getSomeFiles()
            }
            
            abstract class MakeGreenAction implements ArtifactTransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                @InputArtifact
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name} using \${parameters.someFiles*.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = "ok"
                }
            }
        """
    }

    def "transform can receive pre-built file collection via parameter object"() {
        settingsFile << """
                include 'a', 'b', 'c'
            """
        setupBuildWithTransformFileInputs()
        buildFile << """
            allprojects {
                ext.inputFiles = files('a.txt', 'b.txt')
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        outputContains("processing b.jar using [a.txt, b.txt]")
        outputContains("processing c.jar using [a.txt, b.txt]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform can receive a file collection containing external dependencies as parameter"() {
        mavenRepo.module("test", "tool-a", "1.2").publish()
        mavenRepo.module("test", "tool-b", "1.2").publish()

        settingsFile << """
                include 'a', 'b', 'c'
            """
        setupBuildWithTransformFileInputs()
        buildFile << """
            allprojects {
                configurations.create("tools") { }
                repositories.maven { url = '${mavenRepo.uri}' }
                ext.inputFiles = configurations.tools
            }
            
            project(':a') {
                dependencies {
                    tools 'test:tool-a:1.2'
                    tools 'test:tool-b:1.2'

                    implementation project(':b')
                    implementation project(':c')
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        outputContains("processing b.jar using [tool-a-1.2.jar, tool-b-1.2.jar]")
        outputContains("processing c.jar using [tool-a-1.2.jar, tool-b-1.2.jar]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform can receive a file collection containing task outputs as parameter"() {
        settingsFile << """
                include 'a', 'b', 'c'
            """
        setupBuildWithTransformFileInputs()
        buildFile << """
            allprojects {
                task tool(type: FileProducer) {
                    output = file("build/tool-\${project.name}.jar")
                }
                ext.inputFiles = files(tool.output)                
            }

            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        // TODO - should run the producer task
//        result.assertTasksExecuted("a:tool", "b:producer", "c:producer", "a:resolve")
        outputContains("processing b.jar using [tool-a.jar]")
        outputContains("processing c.jar using [tool-a.jar]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform can receive a file collection containing transform outputs as parameter"() {
        settingsFile << """
                include 'a', 'b', 'c', 'd', 'e'
            """
        setupBuildWithTransformFileInputs()
        buildFile << """
            abstract class MakeRedAction implements ArtifactTransformAction {
                @InputArtifact
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name} wit MakeRedAction"
                    def output = outputs.file(input.name + ".red")
                    output.text = "ok"
                }
            }

            allprojects {
                def attr = Attribute.of('color', String)
                configurations.create("tools") {
                    canBeConsumed = false
                    attributes.attribute(attr, 'blue')
                }
                ext.inputFiles = configurations.tools.incoming.artifactView {
                    attributes.attribute(attr, 'red')
                }.files
                dependencies {
                    registerTransformAction(MakeRedAction) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                    }
                }
            }
            
            project(':a') {
                dependencies {
                    tools project(':d')
                    tools project(':e')

                    implementation project(':b')
                    implementation project(':c')
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        // TODO wolfs: Build dependencies of transforms required by the parameters are not discovered
        // result.assertTasksExecuted(":b:producer", ":c:producer", , ":d:producer", ":e:producer", ":a:resolve")
        result.assertTasksExecuted(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar using [d.jar.red, e.jar.red]")
        outputContains("processing c.jar using [d.jar.red, e.jar.red]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform can receive a file collection containing substituted external dependencies as parameter"() {
        file("tools/settings.gradle") << """
            include 'tool-a', 'tool-b'
        """
        file("tools/build.gradle") << """
            allprojects { 
                group = 'test'
                configurations.create("default")
                task producer(type: Producer) {
                    outputFile = file("build/\${project.name}.jar")
                }
                artifacts."default" producer.outputFile
            }
            
            class Producer extends DefaultTask {
                @OutputFile
                RegularFileProperty outputFile = project.objects.fileProperty()
            
                @TaskAction
                def go() {
                    outputFile.get().asFile.text = "output"
                }
            }
        """
        settingsFile << """
                include 'a', 'b', 'c'
                includeBuild 'tools'
            """
        setupBuildWithTransformFileInputs()
        buildFile << """
            allprojects {
                configurations.create("tools") { }
                ext.inputFiles = configurations.tools
            }
            
            project(':a') {
                dependencies {
                    tools 'test:tool-a:1.2'
                    tools 'test:tool-b:1.2'

                    implementation project(':b')
                    implementation project(':c')
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        // TODO - should run the producer tasks
//        result.assertTasksExecuted(":tools:tool-a:producer", ":tools:tool-b:producer", ":b:producer", "c:producer", ":a:resolve")
        outputContains("processing b.jar using [tool-a.jar, tool-b.jar]")
        outputContains("processing c.jar using [tool-a.jar, tool-b.jar]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }
}
