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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@ValidationTestFor(
    ValidationProblemId.IMPLICIT_DEPENDENCY
)
class MissingTaskDependenciesIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "detects missing dependency between two tasks and fails (#description)"() {
        buildFile << """
            task producer {
                def outputFile = file("${producedLocation}")
                outputs.${outputType}(${producerOutput == null ? 'outputFile' : "'${producerOutput}'"})
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("${consumedLocation}")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        when:
        runAndFail("producer", "consumer")
        then:
        assertMissingDependency(":producer", ":consumer", file(consumedLocation))

        where:
        description            | producerOutput | outputType | producedLocation           | consumedLocation
        "same location"        | null           | "file"     | "output.txt"               | "output.txt"
        "consuming ancestor"   | null           | "file"     | "build/dir/sub/output.txt" | "build/dir"
        "consuming descendant" | 'build/dir'    | "dir"      | "build/dir/sub/output.txt" | "build/dir/sub/output.txt"
    }

    def "ignores missing dependency if there is an #relation relation in the other direction"() {
        def sourceDir = "src"
        file(sourceDir).createDir()
        def outputDir = "build/output"

        buildFile << """
            task firstTask {
                inputs.dir("${sourceDir}")
                def outputDir = file("${outputDir}")
                outputs.dir(outputDir)
                doLast {
                    new File(outputDir, "source").text = "fixed"
                }
            }

            task secondTask {
                def inputDir = file("${outputDir}")
                def outputDir = file("${sourceDir}")
                inputs.dir(inputDir)
                outputs.dir(outputDir)
                doLast {
                    new File(outputDir, "source").text = "fixed"
                }
            }

            secondTask.${relation}(firstTask)
        """

        expect:
        succeeds("firstTask", "secondTask")
        succeeds("firstTask", "secondTask")

        where:
        relation << ['dependsOn', 'mustRunAfter']
    }

    def "does not detect missing dependency when consuming the sibling of the output of the producer"() {
        buildFile << """
            task producer {
                def outputFile = file("build/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("build/notOutput.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        expect:
        succeeds("producer", "consumer")
        succeeds("consumer", "producer")
    }

    def "transitive dependencies are accepted as valid dependencies (including #dependency)"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            task a
            task b
            task c
            task d

            consumer.dependsOn(d)

            d.dependsOn(c)
            ${dependency}
            b.dependsOn(a)

            a.dependsOn(producer)
        """

        expect:
        // We add the intermediate tasks here, since the dependency relation doesn't necessarily force their scheduling
        succeeds("producer", "b", "c", "consumer")

        where:
        dependency          | _
        "c.dependsOn(b)"    | _
        "c.mustRunAfter(b)" | _
        "b.finalizedBy(c)"  | _
    }

    def "only having shouldRunAfter fails"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            consumer.shouldRunAfter(producer)
        """

        when:
        runAndFail("producer", "consumer")
        then:
        assertMissingDependency(":producer", ":consumer", file("output.txt"))
    }

    def "fails with missing dependencies even if the consumer does not have outputs"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                inputs.files(inputFile)
                doLast {
                    println "Hello " + inputFile.text
                }
            }
        """

        when:
        runAndFail("producer", "consumer")
        then:
        assertMissingDependency(":producer", ":consumer", file("output.txt"))
    }

    def "does not report missing dependencies when #disabledTask is disabled"() {
        buildFile << """
            task producer {
                def outputFile = file("build/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("build/output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            ${disabledTask}.enabled = false
        """

        when:
        run(":producer", ":consumer")
        then:
        executed(":producer", ":consumer")

        when:
        run(":consumer", ":producer")
        then:
        executed(":producer", ":consumer")

        where:
        disabledTask << ["consumer", "producer"]
    }

    def "takes filters for inputs into account when detecting missing dependencies"() {
        file("src/main/java/MyClass.java").createFile()
        buildFile << """
            task producer {
                def outputFile = file("build/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "first"
                }
            }
            task filteredConsumer(type: Zip) {
                from(project.projectDir) {
                    include 'src/**'
                }
                destinationDirectory = file("build")
                archiveBaseName = "output3"
            }
        """

        when:
        run("producer", "filteredConsumer")
        then:
        executedAndNotSkipped(":producer", ":filteredConsumer")
        when:
        run("filteredConsumer", "producer")
        then:
        skipped(":producer", ":filteredConsumer")
    }

    def "fails when missing dependencies using filtered inputs"() {
        file("src/main/java/MyClass.java").createFile()
        buildFile << """
            task producer {
                def outputFile = file("build/problematic/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "first"
                }
            }
            task consumer(type: Zip) {
                from(project.projectDir) {
                    include 'build/problematic/**'
                }
                destinationDirectory = file("build")
                archiveBaseName = "outputZip"
            }
        """

        when:
        runAndFail("producer", "consumer")
        then:
        assertMissingDependency(":producer", ":consumer", testDirectory)
    }

    @Issue("https://github.com/gradle/gradle/issues/16061")
    def "does not detect missing dependencies even for complicated filters"() {
        buildFile """
            task prepareBuild {
                outputs.file("app/foo.txt")
                doLast {}
            }

            def sources = fileTree("app") {
                include("**/*.txt")
                exclude("**/*generated*")
                builtBy(prepareBuild)
            }

            task consumesResultOfPrepareBuildAndGeneratesAInSameDirectory {
                inputs.files(sources)
                outputs.file("app/src/generatedA.txt")
                doLast {}
            }

            task consumesResultOfPrepareBuildAndGeneratesBInSameDirectory {
                inputs.files(sources)
                outputs.file("app/src/generatedB.txt")
                doLast {}
            }

            task assemble {
                dependsOn(consumesResultOfPrepareBuildAndGeneratesAInSameDirectory, consumesResultOfPrepareBuildAndGeneratesBInSameDirectory)
            }
        """

        when:
        run("assemble")
        then:
        executedAndNotSkipped(":assemble")
    }

    @Issue("https://github.com/gradle/gradle/issues/16061")
    def "missing dependency detection takes excludes into account"() {
        file("src/main/java/my/JavaClass.java").text = """
            package my;

            public class JavaClass {}
        """

        buildFile """
            task produceInBuild {
                def outputFile = file("build/app/foo.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "output"
                }
            }

            task showSources {
                def sources = fileTree(projectDir) {
                    exclude "build"
                    exclude ".gradle"
                    exclude "build.gradle"
                    exclude "settings.gradle"
                    exclude "operations-log.txt"
                }
                inputs.files(sources)
                doLast {
                    sources.each {
                        println it.name
                        assert it.name == "JavaClass.java"
                    }
                }
            }
        """

        when:
        run("produceInBuild", "showSources")
        then:
        outputContains("JavaClass.java")
        executedAndNotSkipped(":produceInBuild", ":showSources")
    }

    @Issue("https://github.com/gradle/gradle/issues/17561")
    def "missing dependency detection takes ** excludes for non-existing files into account"() {
        file("build/my/some.foo") << "foo!"
        file("build/other/some.bar") << "bar!"

        buildFile """
            task fooReport {
                inputs.files(fileTree(buildDir) { include("**/*.foo")})
                def reportPath = file("\${buildDir}/fooReport.txt")
                outputs.file(reportPath)
                doLast {
                    reportPath.text = "foo"
                }
            }
            task barReport {
                inputs.files(fileTree(buildDir) { include("**/*.bar")})
                def reportPath = file("\${buildDir}/barReport.txt")
                outputs.file(reportPath)
                doLast {
                    reportPath.text = "bar"
                }
            }
        """

        when:
        run("fooReport", "barReport")
        then:
        executedAndNotSkipped(":fooReport", ":barReport")
    }

    @Issue("https://github.com/gradle/gradle/issues/20391")
    def "running tasks in parallel with exclusions does not cause incorrect builds"() {
        // This test is inspired by our build setup where we found this problem:
        // We zip the source distribution by using an archive task starting from the root project.
        // This caused problems when building the JARs in parallel.
        // We introduced a workaround for our build in https://github.com/gradle/gradle/pull/20366.

        // Configuration caching resolves the inputs once more to store the result in the configuration cache.
        int countResolvedBeforeTaskExecution = GradleContextualExecuter.configCache ? 2 : 1
        server.start()
        file("lib/src/MyClass.java").text = "public class MyClass {}"

        settingsFile """
            include "dist"
            include "lib"
        """

        file("dist/build.gradle").text = """
            abstract class ZipSrc extends DefaultTask {
                @Internal
                int countResolved

                @Internal
                abstract DirectoryProperty getSources()

                @InputFiles
                abstract ConfigurableFileCollection getSourceFiles()

                @OutputFile
                abstract RegularFileProperty getZipFile()

                ZipSrc() {
                    // We need a way to count access times, that is why I ended up with configuring it in the task so it has access to countResolved.
                    // I didn't find a way to make the test configuration cache compatible without the extra sources property and doing this configuration in the task registration.
                    sourceFiles.from(sources.map {
                        if (countResolved == ${countResolvedBeforeTaskExecution}) {
                            println "resolving input"
                            ${server.callFromBuild("zipFileSnapshottingStarted")}
                        }
                        countResolved++
                        it.asFileTree.matching {
                            include "src/**"
                            include "build.gradle"
                        }
                    })
                }

                @TaskAction
                void zipSources() {
                    ${server.callFromBuild("zipFileSnapshottingFinished")}
                    zipFile.get().asFile.text = "output"
                }
            }

            task srcZip(type: ZipSrc) {
                sources = rootProject.file("lib")
                zipFile = file("build/srcZip.zip")
            }
        """

        file("lib/build.gradle").text = """

            abstract class Compile extends DefaultTask {
                @InputDirectory
                abstract DirectoryProperty getSources()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void compile() {
                    ${server.callFromBuild("compileAction1")}
                    ${server.callFromBuild("compileAction2")}
                    outputFile.get().asFile.text = "classes"
                }
            }

            task compile(type: Compile) {
                sources.fileValue(file("src"))
                outputFile = file("classes.jar")
            }
        """

        // This is to make sure that:
        //   - The snapshotting of the zip task finishes after the outputs have been broadcast by the compile task
        //   - The snapshotting of the zip task finishes before the snapshotting of the outputs of the compile task
        server.expectConcurrent("zipFileSnapshottingStarted", "compileAction1")
        server.expectConcurrent("zipFileSnapshottingFinished", "compileAction2")
        when:
        run "srcZip", "compile", "--parallel"
        then:
        executedAndNotSkipped(":dist:srcZip", ":lib:compile")
        file("lib/classes.jar").text == "classes"
        server.expect("compileAction1")
        server.expect("compileAction2")
        when:
        assert file("lib/classes.jar").delete()
        run ":lib:compile"
        then:
        executedAndNotSkipped(":lib:compile")
    }

    def "fails when an input file collection can't be resolved"() {
        buildFile """
            task "broken" {
                inputs.files(5).withPropertyName("invalidInputFileCollection")

                doLast {
                    println "success"
                }
            }
        """
        def cause = """Cannot convert the provided notation to a File or URI: 5.
The following types/formats are supported:
  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'.
  - A String or CharSequence URI, for example 'file:/usr/include'.
  - A File instance.
  - A Path instance.
  - A Directory instance.
  - A RegularFile instance.
  - A URI or URL instance.
  - A TextResource instance."""

        when:
        fails "broken"
        then:
        executedAndNotSkipped ":broken"
        failureDescriptionContains("Execution failed for task ':broken'.")
        failureCauseContains(cause)
    }

    void assertMissingDependency(String producerTask, String consumerTask, File producedConsumedLocation) {
        expectReindentedValidationMessage()
        def expectedMessage = implicitDependency {
            at(producedConsumedLocation)
            consumer(consumerTask)
            producer(producerTask)
            includeLink()
        }
        failure.assertThatDescription(containsNormalizedString(expectedMessage))
    }
}
