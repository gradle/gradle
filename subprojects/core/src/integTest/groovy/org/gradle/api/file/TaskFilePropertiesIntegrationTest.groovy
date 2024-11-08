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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

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
        result.assertTasksNotSkipped(":transform")

        when:
        run("transform")

        then:
        result.assertTasksSkipped(":transform")

        when:
        file("dir1/file3.txt").text = "new"
        run("transform")

        then:
        result.assertTasksNotSkipped(":transform")
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
        result.assertTasksNotSkipped(":transform")

        when:
        run("transform")

        then:
        result.assertTasksSkipped(":transform")

        when:
        file("dir1/file3.txt").text = "new"
        run("transform")

        then:
        result.assertTasksNotSkipped(":transform")
    }

    def "task dependencies are inferred from contents of input FileCollection"() {
        // Include a configuration with transitive dep on a Jar and an unmanaged Jar.
        settingsFile 'include "a", "b"'

        buildFile('a/build.gradle', """
            configurations {
                compile
            }

            dependencies {
                compile project(path: ':b', configuration: 'outgoing')
            }

            tasks.register('doStuff', InputTask) {
                src = configurations.compile + fileTree('src/java')
            }

            class InputTask extends DefaultTask {
                @InputFiles
                def FileCollection src
            }
        """)
        buildFile('b/build.gradle', """
            tasks.register("jar") {
                def jarFile = file('b.jar')
                doLast {
                    jarFile.text = 'some jar'
                }
            }

            tasks.register("otherJar", Jar) {
                destinationDirectory = buildDir
            }

            configurations {
                create("deps")
                consumable("outgoing") {
                    extendsFrom deps
                }
            }

            dependencies {
                deps files('b.jar') {
                    builtBy jar
                }
            }

            artifacts {
                outgoing otherJar
            }
        """)

        when:
        run("doStuff")

        then:
        result.assertTasksExecutedInOrder(any(':b:jar', ':b:otherJar'), ':a:doStuff')
    }

}
