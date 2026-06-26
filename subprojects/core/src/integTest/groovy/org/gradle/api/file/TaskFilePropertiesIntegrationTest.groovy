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

package org.gradle.api.file

import org.gradle.api.problems.Severity
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.any

class TaskFilePropertiesIntegrationTest extends AbstractIntegrationSpec {

    def "task can use Path to represent input and output locations on annotated properties"() {
        buildFile """
            import java.nio.file.Path
            import java.nio.file.Files

            class TransformTask extends DefaultTask {
                @InputFile
                Path inputFile
                @InputDirectory
                Path inputDir
                @OutputFile
                Path outputFile
                @OutputDirectory
                Path outputDir

                @TaskAction
                def go() {
                    outputFile.toFile().text = inputFile.toFile().text
                    inputDir.toFile().listFiles().each { f -> outputDir.resolve(f.name).toFile().text = f.text }
                }
            }

            task transform(type: TransformTask) {
                inputFile = file("file1.txt").toPath()
                inputDir = file("dir1").toPath()
                outputFile = file("build/file1.txt").toPath()
                outputDir = file("build/dir1").toPath()
            }
"""

        when:
        file("file1.txt").text = "123"
        file("dir1/file2.txt").text = "1234"
        run("transform")

        then:
        file("build/file1.txt").text == "123"
        file("build/dir1/file2.txt").text == "1234"

        when:
        run("transform")

        then:
        result.assertTasksSkipped(":transform")

        when:
        file("file1.txt").text = "321"
        run("transform")

        then:
        result.assertTasksExecuted(":transform")

        when:
        run("transform")

        then:
        result.assertTasksSkipped(":transform")

        when:
        file("dir1/file3.txt").text = "new"
        run("transform")

        then:
        result.assertTasksExecuted(":transform")
    }

    def "task can use Path to represent input and output locations on ad hoc properties"() {
        buildFile """
            import java.nio.file.Path
            import java.nio.file.Files

            task transform {
                def inputFile = file("file1.txt").toPath()
                def inputDir = file("dir1").toPath()
                def outputFile = file("build/file1.txt").toPath()
                def outputDir = file("build/dir1").toPath()
                inputs.file(inputFile)
                inputs.dir(inputDir)
                outputs.file(outputFile)
                outputs.dir(outputDir)
                doLast {
                    Files.createDirectories(outputFile.parent)
                    Files.createDirectories(outputDir)
                    outputFile.toFile().text = inputFile.toFile().text
                    inputDir.toFile().listFiles().each { f -> outputDir.resolve(f.name).toFile().text = f.text }
                }
            }
"""

        when:
        file("file1.txt").text = "123"
        file("dir1/file2.txt").text = "1234"
        run("transform")

        then:
        file("build/file1.txt").text == "123"
        file("build/dir1/file2.txt").text == "1234"

        when:
        run("transform")

        then:
        result.assertTasksSkipped(":transform")

        when:
        file("file1.txt").text = "321"
        run("transform")

        then:
        result.assertTasksExecuted(":transform")

        when:
        run("transform")

        then:
        result.assertTasksSkipped(":transform")

        when:
        file("dir1/file3.txt").text = "new"
        run("transform")

        then:
        result.assertTasksExecuted(":transform")
    }

    def "task dependencies are inferred from contents of input FileCollection"() {
        // Include a configuration with transitive dep on a Jar and an unmanaged Jar.
        settingsFile 'include "a", "b"'

        buildFile('a/build.gradle', '''
            configurations.create("compile")
            dependencies { compile project(path: ':b', configuration: 'producer') }

            task doStuff(type: InputTask) {
                src = configurations.compile + fileTree('src/java')
            }

            class InputTask extends DefaultTask {
                @InputFiles
                def FileCollection src
            }
        ''')
        buildFile('b/build.gradle', '''
            apply plugin: 'base'
            task jar {
                def jarFile = file('b.jar')
                doLast {
                    jarFile.text = 'some jar'
                }
            }

            task otherJar(type: Jar) {
                destinationDirectory = buildDir
            }

            configurations {
                create("deps")
                consumable("producer") {
                    extendsFrom deps
                    outgoing.artifact(otherJar)
                }
            }
            dependencies { deps files('b.jar') { builtBy jar } }
        ''')

        when:
        run("doStuff")

        then:
        result.assertTasksScheduledInOrder(any(':b:jar', ':b:otherJar'), ':a:doStuff')
    }

    @Issue("https://github.com/gradle/gradle/issues/38330")
    def "optional runtime input declared via #description with an absent provider source is ignored"() {
        buildFile """
            task myTask {
                $declaration

                doLast {
                    println("inputs = \${inputs.files.files}")
                }
            }
        """

        when:
        run "myTask"

        then:
        executedAndNotSkipped(":myTask")

        where:
        description                                | declaration
        "inputs.files(fileProperty)"               | 'inputs.files(objects.fileProperty()).optional().withPropertyName("inputProp")'
        "inputs.files(fileProperty, fileProperty)" | 'inputs.files([objects.fileProperty()]).withPropertyName("inputProp")'
        "inputs.file(fileProperty)"                | 'inputs.file(objects.fileProperty()).optional().withPropertyName("inputProp")'
        "inputs.property(property)"                | 'inputs.property("inputProp", objects.property(String)).optional(true)'
    }

    @Issue("https://github.com/gradle/gradle/issues/38330")
    def "required runtime input declared via #description with an absent provider source fails"() {
        buildFile """
            task myTask {
                $declaration

                doLast {
                    println("inputs = \${inputs.files.files}")
                }
            }
        """

        enableProblemsApiCheck()

        when:
        fails "myTask"

        then:
        failure.assertHasDescription("A problem was found with the configuration of task ':myTask' (type 'DefaultTask').")
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'validation:property-validation:value-not-set'
            definition.id.displayName == 'Value not set'
            contextualLabel == "Property 'inputProp' doesn't have a configured value"
        }

        where:
        description                  | declaration
        "inputs.files(fileProperty)" | 'inputs.files(objects.fileProperty()).withPropertyName("inputProp")'
        "inputs.file(fileProperty)"  | 'inputs.file(objects.fileProperty()).withPropertyName("inputProp")'
        "inputs.property(property)"  | 'inputs.property("inputProp", objects.property(String))'
    }

    @Issue("https://github.com/gradle/gradle/issues/38330")
    def "required runtime input declared via files() with an absent provider in a list source is ignored"() {
        buildFile """
            task myTask {
                inputs.files([objects.fileProperty()]).withPropertyName("inputProp")

                doLast {
                    println("inputs = \${inputs.files.files}")
                }
            }
        """

        enableProblemsApiCheck()

        when:
        run "myTask"

        then:
        outputContains("inputs = []")
    }
}
