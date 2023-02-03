/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SourceTaskIntegrationTest extends AbstractIntegrationSpec {
    def "can specify source files using a Groovy closure"() {
        given:
        file("src/one.txt").createFile()
        file("src/a/two.txt").createFile()

        buildFile << """
            class TestTask extends SourceTask {
                @TaskAction
                def list() {
                    source.visit { fte -> println("visit " + fte.relativePath) }
                }
            }

            def location = null

            task source(type: TestTask) {
                source { file(location) }
            }

            location = 'src'
        """

        when:
        run "source"

        then:
        output.count("visit ") == 3
        outputContains("visit one.txt")
        outputContains("visit a")
        outputContains("visit a/two.txt")

        when:
        file("src/a/three.txt").createFile()
        run "source"

        then:
        output.count("visit ") == 4
        outputContains("visit one.txt")
        outputContains("visit a")
        outputContains("visit a/two.txt")
        outputContains("visit a/three.txt")
    }

    def "can disable empty directory normalization for classes that extend SourceTask"() {
        given:
        file("src/one.txt").createFile()
        file("src/a/two.txt").createFile()

        buildFile << """
            class TestTask extends SourceTask {
                @OutputFile
                File outputFile

                TestTask() {
                    inputs.files({ -> super.source })
                        .ignoreEmptyDirectories(false)
                        .withPathSensitivity(PathSensitivity.ABSOLUTE)
                        .withPropertyName("sourcesWithEmptyDirectories")
                }

                @TaskAction
                def list() {
                    source.visit { fte -> println("visit " + fte.relativePath) }
                    outputFile.text = 'executed'
                }
            }

            task source(type: TestTask) {
                source file('src')
                outputFile = file("\${buildDir}/output")
            }
        """

        when:
        run "source"

        then:
        output.count("visit ") == 3
        outputContains("visit one.txt")
        outputContains("visit a")
        outputContains("visit a/two.txt")

        when:
        run "source"

        then:
        skipped(":source")

        and:
        output.count("visit ") == 0

        when:
        file("src/a/emptyDir").mkdirs()
        run "source"

        then:
        executedAndNotSkipped(":source")
        output.count("visit ") == 4
        outputContains("visit one.txt")
        outputContains("visit a")
        outputContains("visit a/two.txt")
        outputContains("visit a/emptyDir")
    }

    def "can override getSource() method in SourceTask and still ignore empty directories"() {
        given:
        file("src/one.txt").createFile()
        file("src/a/two.txt").createFile()

        buildFile << """
            class TestTask extends SourceTask {
                @OutputFile
                File outputFile

                @TaskAction
                def list() {
                    source.visit { fte -> println("visit " + fte.relativePath) }
                    outputFile.text = 'executed'
                }

                @InputFiles
                @SkipWhenEmpty
                @PathSensitive(PathSensitivity.ABSOLUTE)
                @Override
                public FileTree getSource() {
                    return super.getSource()
                }
            }

            task source(type: TestTask) {
                source file('src')
                outputFile = file("\${buildDir}/output")
            }
        """

        when:
        run "source"

        then:
        output.count("visit ") == 3
        outputContains("visit one.txt")
        outputContains("visit a")
        outputContains("visit a/two.txt")

        when:
        run "source"

        then:
        skipped(":source")

        and:
        output.count("visit ") == 0

        when:
        file("src/a/emptyDir").mkdirs()
        run "source"

        then:
        skipped(":source")
        output.count("visit ") == 0
    }
}
