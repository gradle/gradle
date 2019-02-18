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

import org.gradle.api.tasks.PathSensitivity
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.util.ToBeImplemented
import spock.lang.Unroll

class ArtifactTransformWithFileInputsIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {
    /**
     * Caller should add define an 'inputFiles' property to define the inputs to the transform
     */
    def setupBuildWithTransformFileInputs(String inputAnnotations = "@InputFiles") {
        setupBuildWithColorTransform {
            params("""
                someFiles.from { project.inputFiles }
            """)
        }
        buildFile << """
            @AssociatedTransformAction(MakeGreenAction)
            interface MakeGreen {
                $inputAnnotations
                ConfigurableFileCollection getSomeFiles()
            }
            
            abstract class MakeGreenAction implements TransformAction {
                @TransformParameters
                abstract MakeGreen getParameters()
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        result.assertTasksExecuted(":a:tool", ":b:producer", ":c:producer", ":a:resolve")
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
            abstract class MakeRedAction implements TransformAction {
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        result.assertTasksExecuted(":b:producer", ":c:producer", ":d:producer", ":e:producer", ":a:resolve")
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
        result.assertTasksExecuted(":tools:tool-a:producer", ":tools:tool-b:producer", ":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar using [tool-a.jar, tool-b.jar]")
        outputContains("processing c.jar using [tool-a.jar, tool-b.jar]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform does not execute when file inputs cannot be built"() {
        settingsFile << """
                include 'a', 'b', 'c'
            """
        setupBuildWithTransformFileInputs()
        buildFile << """
            allprojects {
                task tool(type: FileProducer) {
                    output = file("build/tool-\${project.name}.jar")
                    doLast { throw new RuntimeException('broken') }
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
        fails(":a:resolve")

        then:
        result.assertTasksExecuted(":a:tool")
        outputDoesNotContain("processing")
        failure.assertHasDescription("Execution failed for task ':a:tool'.")
        failure.assertHasFailures(1)
        failure.assertHasCause("broken")
    }

    @Unroll
    def "can use input path sensitivity #pathSensitivity for parameter object"() {
        settingsFile << """
                include 'a', 'b', 'c'
            """
        setupBuildWithTransformFileInputs("@PathSensitive(PathSensitivity.$pathSensitivity) @InputFiles")
        buildFile << """
            allprojects {
                ext.inputFiles = files(rootProject.file(project.property('fileName')))
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
        """
        def (first, second, third) = files

        when:
        def firstFilePath = first[0]
        def firstFile = file(firstFilePath)
        firstFile.text = first[1]
        run(":a:resolve", "-PfileName=${firstFilePath}")
        then:
        outputContains("processing b.jar using [$firstFile.name]")
        outputContains("processing c.jar using [$firstFile.name]")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        file(second[0]).text = second[1]
        run(":a:resolve", "-PfileName=${second[0]}")
        then:
        outputDoesNotContain("Transform artifact")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        def thirdFilePath = third[0]
        def thirdFile = file(thirdFilePath)
        thirdFile.text = third[1]
        run(":a:resolve", "-PfileName=${thirdFilePath}")
        then:
        outputContains("processing b.jar using [$thirdFile.name]")
        outputContains("processing c.jar using [$thirdFile.name]")
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        pathSensitivity           | files
        PathSensitivity.NONE      | [['first', 'foo'], ['second', 'foo'], ['third', 'bar']]
        PathSensitivity.NAME_ONLY | [['first/input', 'foo'], ['second/input', 'foo'], ['third/input1', 'foo']]
        PathSensitivity.RELATIVE  | [['first/input', 'foo'], ['second/input', 'foo'], ['third/input1', 'foo']]
        PathSensitivity.ABSOLUTE  | [['first/input', 'foo'], ['first/input', 'foo'], ['third/input', 'foo']]
    }

    @ToBeImplemented
    def "can use classpath normalization for parameter object"() {
        settingsFile << """
                include 'a', 'b', 'c'
            """
        setupBuildWithTransformFileInputs("@Classpath")
        buildFile << """
            allprojects {
                ext.inputFiles = files(rootProject.file("inputDir"), rootProject.file("input.jar"))
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
                normalization {
                    runtimeClasspath {
                        ignore("**/ignored.txt")
                    }
                }
            }
        """
        def inputDir = file("inputDir")
        inputDir.mkdirs()
        def inputJar = file("input.jar")
        def jarSources = file("jarDir")
        jarSources.mkdirs()

        when:
        inputDir.file("org/gradle/input.txt").text = "input.txt"
        inputDir.file("org/gradle/ignored.txt").text = "some"
        jarSources.file("some/other/ignored.txt").text = "other"
        jarSources.zipTo(inputJar)
        run(":a:resolve")
        then:
        outputContains("processing b.jar using [${inputDir.name}, ${inputJar.name}]")
        outputContains("processing c.jar using [${inputDir.name}, ${inputJar.name}]")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        inputDir.file("other/ignored.txt").text = "ignored as well"
        jarSources.file("some/other/ignored.txt").text = "ignored change"
        jarSources.zipTo(inputJar)
        run(":a:resolve")
        then:
        // TODO: Should not report changes
        // outputDoesNotContain("Transform artifact")
        outputContains("processing b.jar using [${inputDir.name}, ${inputJar.name}]")
        outputContains("processing c.jar using [${inputDir.name}, ${inputJar.name}]")

        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        jarSources.file("new/input/file.txt").text = "new input"
        jarSources.zipTo(inputJar)
        run(":a:resolve")
        then:
        outputContains("processing b.jar using [${inputDir.name}, ${inputJar.name}]")
        outputContains("processing c.jar using [${inputDir.name}, ${inputJar.name}]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }
}
