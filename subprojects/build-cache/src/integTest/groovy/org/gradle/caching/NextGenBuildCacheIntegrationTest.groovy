/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching

import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile

class NextGenBuildCacheIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def "compile task is loaded from cache"() {
        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Main.java") << """
            public class Main {}
        """

        when:
        runWithCacheNG "compileJava"
        then:
        executedAndNotSkipped ":compileJava"

        when:
        runWithCacheNG "clean", "compileJava"
        then:
        skipped ":compileJava"
    }

    def "empty output directory is cached properly"() {
        given:
        buildFile << """
            task customTask {
                def outputDir = file("build/empty")
                outputs.dir outputDir withPropertyName "empty"
                outputs.cacheIf { true }
                doLast {
                    outputDir.mkdirs()
                }
            }
        """

        when:
        runWithCacheNG "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/empty").assertIsEmptyDir()

        when:
        cleanBuildDir()
        runWithCacheNG "customTask"
        then:
        skipped ":customTask"
        file("build/empty").assertIsEmptyDir()
    }

    def "non-cacheable task with cache enabled gets cached"() {
        file("input.txt") << "data"
        buildFile << """
            class NonCacheableTask extends DefaultTask {
                @InputFile inputFile
                @OutputFile outputFile

                @TaskAction copy() {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = inputFile.text
                }
            }
            task customTask(type: NonCacheableTask) {
                inputFile = file("input.txt")
                outputFile = file("\$buildDir/output.txt")
                outputs.cacheIf { true }
            }
        """

        when:
        runWithCacheNG "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        runWithCacheNG "customTask"
        then:
        skipped ":customTask"
    }

    def "cacheable task with multiple outputs declared via runtime API with matching cardinality get cached"() {
        buildFile << """
            task customTask {
                outputs.cacheIf { true }
                outputs.files files("build/output1.txt", "build/output2.txt") withPropertyName("out")
                doLast {
                    file("build/output1.txt") << "data"
                    file("build/output2.txt") << "data"
                }
            }
        """

        when:
        runWithCacheNG "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        runWithCacheNG "customTask"
        then:
        skipped ":customTask"
    }

    def "cacheable task with multiple output properties with matching cardinality get cached"() {
        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputFiles Iterable<File> out

                @TaskAction
                void execute() {
                    out.eachWithIndex { file, index ->
                        file.text = "data\${index + 1}"
                    }
                }
            }

            task customTask(type: CustomTask) {
                out = files("build/output1.txt", "build/output2.txt")
            }
        """

        when:
        runWithCacheNG "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        runWithCacheNG "customTask"
        then:
        skipped ":customTask"
        file("build/output1.txt").text == "data1"
        file("build/output2.txt").text == "data2"
    }

    def "cacheable task with identical output files is cached"() {
        buildFile << """
            @CacheableTask
            abstract class CustomTask extends DefaultTask {
                @OutputDirectory abstract DirectoryProperty getOutputDir()

                @TaskAction
                void execute() {
                    outputDir.file("output1.txt").get().asFile.text = "data"
                    outputDir.file("output2.txt").get().asFile.text = "data"
                    outputDir.file("output3.txt").get().asFile.text = "data"
                }
            }

            task customTask(type: CustomTask) {
                outputDir = layout.buildDir
            }
        """

        when:
        runWithCacheNG "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output1.txt").text == "data"
        file("build/output2.txt").text == "data"
        file("build/output3.txt").text == "data"

        when:
        cleanBuildDir()
        runWithCacheNG "customTask"
        then:
        skipped ":customTask"
        file("build/output1.txt").text == "data"
        file("build/output2.txt").text == "data"
        file("build/output3.txt").text == "data"
    }

    def "ad hoc tasks are not cacheable by default"() {
        given:
        file("input.txt") << "data"
        buildFile << adHocTaskWithInputs()

        expect:
        runWithCacheNG ":adHocTask"
        executedAndNotSkipped ":adHocTask"
        cleanBuildDir()

        runWithCacheNG ":adHocTask"
        executedAndNotSkipped ":adHocTask"
    }

    def "ad hoc tasks are cached when explicitly requested"() {
        given:
        file("input.txt") << "data"
        buildFile << adHocTaskWithInputs()
        buildFile << 'adHocTask { outputs.cacheIf { true } }'

        expect:
        runWithCacheNG ":adHocTask"
        executedAndNotSkipped ":adHocTask"
        cleanBuildDir()

        runWithCacheNG ":adHocTask"
        skipped ":adHocTask"
    }

    private runWithCacheNG(String... task) {
        run Lists.asList("-Dorg.gradle.unsafe.cache.ng=true", task)
    }

    private TestFile cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }

    private static String adHocTaskWithInputs() {
        """
        task adHocTask {
            def inputFile = file("input.txt")
            def outputFile = file("\$buildDir/output.txt")
            inputs.file(inputFile)
            outputs.file(outputFile)
            doLast {
                outputFile.parentFile.mkdirs()
                outputFile.text = inputFile.text
            }
        }
        """.stripIndent()
    }

}
