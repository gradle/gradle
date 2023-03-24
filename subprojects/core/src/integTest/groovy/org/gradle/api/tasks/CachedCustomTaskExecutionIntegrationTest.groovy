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

package org.gradle.api.tasks


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

import static org.gradle.api.tasks.LocalStateFixture.defineTaskWithLocalState
import static org.gradle.util.internal.TextUtil.escapeString

class CachedCustomTaskExecutionIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, ValidationMessageChecker {

    private static final String CACHE_KEY_PATTERN = "[0-9a-f]+"

    def configureCacheForBuildSrc() {
        file("buildSrc/settings.gradle") << localCacheConfiguration()
    }

    def "buildSrc is loaded from cache"() {
        configureCacheForBuildSrc()
        file("buildSrc/src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*

            class MyTask extends DefaultTask {}
        """

        when:
        withBuildCache().run "help"
        then:
        result.assertTaskNotSkipped(":buildSrc:compileGroovy")

        expect:
        file("buildSrc/build").assertIsDir().deleteDir()

        when:
        withBuildCache().run "help"
        then:
        result.groupedOutput.task(":buildSrc:compileGroovy").outcome == "FROM-CACHE"
    }

    def "tasks stay cached after buildSrc with custom Groovy task is rebuilt"() {
        configureCacheForBuildSrc()
        file("buildSrc/src/main/groovy/CustomTask.groovy") << defineCachedTask()
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        withBuildCache().run "customTask"
        then:
        result.assertTaskNotSkipped(":customTask")

        when:
        file("buildSrc/build").deleteDir()
        file("buildSrc/.gradle").deleteDir()
        cleanBuildDir()

        withBuildCache().run "customTask"
        then:
        result.groupedOutput.task(":customTask").outcome == "FROM-CACHE"
    }

    def "changing custom Groovy task implementation in buildSrc invalidates its cached result"() {
        configureCacheForBuildSrc()
        def taskSourceFile = file("buildSrc/src/main/groovy/CustomTask.groovy")
        taskSourceFile << defineCachedTask()
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        withBuildCache().run "customTask"
        then:
        result.assertTaskNotSkipped(":customTask")
        file("build/output.txt").text == "input"

        when:
        taskSourceFile.text = defineCachedTask(" modified")

        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        result.assertTaskNotSkipped(":customTask")
        file("build/output.txt").text == "input modified"
    }

    def "cacheable task with cache disabled doesn't get cached"() {
        configureCacheForBuildSrc()
        file("input.txt") << "data"
        file("buildSrc/src/main/groovy/CustomTask.groovy") << defineCachedTask()
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file("input.txt")
                outputFile = file("\$buildDir/output.txt")
            }
        """

        when:
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"


        when:
        buildFile << """
            customTask.outputs.cacheIf { false }
        """

        withBuildCache().run "customTask"
        cleanBuildDir()

        withBuildCache().run "customTask"

        then:
        executedAndNotSkipped ":customTask"
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
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
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
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/output1.txt").text == "data1"
        file("build/output2.txt").text == "data2"
    }

    def "cacheable task with multiple outputs with not matching cardinality don't get cached"() {
        buildFile << """
            task customTask {
                outputs.cacheIf { true }
                def fileList
                if (providers.systemProperty("changedCardinality").present) {
                    fileList = ["build/output1.txt"]
                } else {
                    fileList = ["build/output1.txt", "build/output2.txt"]
                }
                outputs.files files(fileList) withPropertyName("out")
                def output1 = project.file("build/output1.txt")
                def output2 = project.file("build/output2.txt")
                doLast {
                    output1.parentFile.mkdirs()
                    output1 << "data"
                    if (!System.getProperty("changedCardinality")) {
                        output2 << "data"
                    }
                }
            }
        """

        when:
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        withBuildCache().run "customTask", "-DchangedCardinality=yes"
        then:
        executedAndNotSkipped ":customTask"
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
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
    }

    def "ad hoc tasks are not cacheable by default"() {
        given:
        file("input.txt") << "data"
        buildFile << adHocTaskWithInputs()

        expect:
        taskIsNotCached ':adHocTask'
    }

    def "ad hoc tasks are cached when explicitly requested"() {
        given:
        file("input.txt") << "data"
        buildFile << adHocTaskWithInputs()
        buildFile << 'adHocTask { outputs.cacheIf { true } }'

        expect:
        taskIsCached ':adHocTask'
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

    def "optional file output is not stored when there is no output"() {
        configureCacheForBuildSrc()
        file("input.txt") << "data"
        file("buildSrc/src/main/groovy/CustomTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @OutputFile
                File outputFile

                @Optional
                @OutputFile
                File secondaryOutputFile

                @TaskAction
                void doSomething() {
                    outputFile.text = inputFile.text
                    if (secondaryOutputFile != null) {
                        secondaryOutputFile.text = "secondary"
                    }
                }
            }
        """
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file("input.txt")
                outputFile = file("build/output.txt")
                secondaryOutputFile = file("build/secondary.txt")
            }
        """

        when:
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output.txt").text == "data"
        file("build/secondary.txt").text == "secondary"
        file("build").listFiles().sort() as List == [file("build/output.txt"), file("build/secondary.txt")]

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/output.txt").text == "data"
        file("build/secondary.txt").text == "secondary"
        file("build").listFiles().sort() as List == [file("build/output.txt"), file("build/secondary.txt")]

        when:
        cleanBuildDir()
        buildFile << """
            customTask.secondaryOutputFile = null
        """
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output.txt").text == "data"
        file("build").listFiles().sort() as List == [file("build/output.txt")]

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/output.txt").text == "data"
        file("build").listFiles().sort() as List == [file("build/output.txt")]
    }

    def "plural output files are only restored when map keys match"() {
        configureCacheForBuildSrc()
        file("input.txt") << "data"
        file("buildSrc/src/main/groovy/CustomTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @OutputFiles
                Map<String, File> outputFiles

                @TaskAction
                void doSomething() {
                    outputFiles.each { String key, File outputFile ->
                        outputFile.text = key
                    }
                }
            }
        """
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file("input.txt")
                outputFiles = [
                    one: file("build/output-1.txt"),
                    two: file("build/output-2.txt")
                ]
            }
        """

        when:
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output-1.txt").text == "one"
        file("build/output-2.txt").text == "two"
        file("build").listFiles().sort() as List == [file("build/output-1.txt"), file("build/output-2.txt")]

        when:
        cleanBuildDir()
        buildFile << """
            customTask.outputFiles = [
                one: file("build/output-a.txt"),
                two: file("build/output-b.txt")
            ]
        """
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/output-a.txt").text == "one"
        file("build/output-b.txt").text == "two"
        file("build").listFiles().sort() as List == [file("build/output-a.txt"), file("build/output-b.txt")]

        when:
        cleanBuildDir()
        buildFile << """
            customTask.outputFiles = [
                first: file("build/output-a.txt"),
                second: file("build/output-b.txt")
            ]
        """
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output-a.txt").text == "first"
        file("build/output-b.txt").text == "second"
        file("build").listFiles().sort() as List == [file("build/output-a.txt"), file("build/output-b.txt")]
    }

    def "missing #type output from runtime API is not cached"() {
        given:
        file("input.txt") << "data"
        buildFile << """
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output.txt" withPropertyName "output"
                outputs.$type "build/output/missing" withPropertyName "missing"
                outputs.cacheIf { true }
                doLast {
                    file("build").mkdirs()
                    file("build/output.txt").text = file("input.txt").text
                    delete("build/output/missing")
                }
            }
        """

        when:
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output.txt").text == "data"
        file("build/output").assertIsDir()
        file("build/output/missing").assertDoesNotExist()

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/output.txt").text == "data"
        file("build/output").assertIsDir()
        file("build/output/missing").assertDoesNotExist()

        where:
        type << ["file", "dir"]
    }

    def "missing #type from annotation API is not cached"() {
        given:
        file("input.txt") << "data"

        buildFile << """
            @CacheableTask
            abstract class CustomTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile = project.file("input.txt")

                @${type}
                File missing = project.file("build/output/missing")

                @OutputFile
                File output = project.file("build/output.txt")

                @Inject
                abstract FileSystemOperations getFs()

                @TaskAction void doSomething() {
                    output.text = inputFile.text
                    fs.delete { delete(missing) }
                }
            }

            task customTask(type: CustomTask)
        """

        when:
        // creates the directory, but not the output file
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/output.txt").text == "data"
        file("build/output").assertIsDir()
        file("build/output/missing").assertDoesNotExist()

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/output.txt").text == "data"
        file("build/output").assertIsDir()
        file("build/output/missing").assertDoesNotExist()

        where:
        type << ["OutputFile", "OutputDirectory"]
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
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        file("build/empty").assertIsEmptyDir()

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        file("build/empty").assertIsEmptyDir()
    }

    def "reports useful error when output #expected is expected but #actual is produced"() {
        given:
        file("input.txt") << "data"
        buildFile << """
            task customTask {
                inputs.file "input.txt"
                outputs.$expected "build/output" withPropertyName "output"
                outputs.cacheIf { true }
                doLast {
                    delete('build')
                    ${
            actual == "file"
                ? "mkdir('build'); file('build/output').text = file('input.txt').text"
                : "mkdir('build/output'); file('build/output/output.txt').text = file('input.txt').text"
        }
                }
            }
        """

        when:
        withBuildCache().fails "customTask"
        then:
        def expectedMessage = message.replace("PATH", file("build/output").path)
        failureHasCause(~/Failed to store cache entry $CACHE_KEY_PATTERN for task ':customTask': .*${escapeString(expectedMessage)}/)
        errorOutput.contains expectedMessage

        where:
        expected | actual | message
        "file"   | "dir"  | "Expected 'PATH' to be a file"
        "dir"    | "file" | "Expected 'PATH' to be a directory"
    }

    def "task stays up-to-date after loaded from cache"() {
        file("input.txt").text = "input"
        buildFile << defineProducerTask()

        withBuildCache().run "producer"

        when:
        cleanBuildDir()
        withBuildCache().run "producer"
        then:
        skipped ":producer"

        when:
        withBuildCache().run "producer", "--info"
        !output.contains("Caching disabled for task ':producer'")
        then:
        skipped ":producer"
    }

    def "task can be cached after loaded from cache"() {
        file("input.txt").text = "input"
        buildFile << defineProducerTask()

        // Store in local cache
        withBuildCache().run "producer"

        // Load from local cache
        cleanBuildDir()
        withBuildCache().run "producer"

        // Store to a different local cache
        def alternativeLocalCache = new TestBuildCache(file("cache-dir-2").createDir())
        settingsFile << alternativeLocalCache.localCacheConfiguration()

        when:
        withBuildCache().run "producer", "--info", "--rerun-tasks"
        then:
        !output.contains("Caching disabled for task ':producer'")

        when:
        // Can load from local cache again
        cleanBuildDir()
        withBuildCache().run "producer"
        then:
        skipped ":producer"
    }

    def "re-ran task is not loaded from cache"() {
        file("input.txt").text = "input"
        buildFile << defineProducerTask()

        // Store in local cache
        withBuildCache().run "producer"

        // Shouldn't load from cache
        when:
        withBuildCache().run "producer", "--rerun-tasks"
        then:
        executed ":producer"
    }

    @Issue("https://github.com/gradle/gradle/issues/3358")
    def "re-ran task is stored in cache"() {
        file("input.txt").text = "input"
        buildFile << defineProducerTask()

        // Store in local cache
        withBuildCache().run "producer", "--rerun-tasks"

        when:
        withBuildCache().run "producer"
        then:
        skipped ":producer"
    }

    def "downstream task stays cached when upstream task is loaded from cache"() {
        file("input.txt").text = "input"
        buildFile << defineProducerTask()
        buildFile << defineConsumerTask()

        withBuildCache().run "consumer"

        when:
        cleanBuildDir()
        withBuildCache().run "consumer"
        then:
        result.assertTasksSkipped(":consumer", ":producer")
    }

    @Issue("https://github.com/gradle/gradle/issues/3043")
    def "URL-quoted characters in file names are handled properly"() {
        def weirdOutputPath = 'build/bad&dir/bad! Dezső %20.txt'
        def expectedOutput = file(weirdOutputPath)
        buildFile << """
            task weirdOutput {
                outputs.dir("build")
                outputs.cacheIf { true }
                def outputFile = file('$weirdOutputPath')
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "Data"
                }
            }
        """

        when:
        withBuildCache().run "weirdOutput"
        then:
        executedAndNotSkipped ":weirdOutput"
        expectedOutput.file

        when:
        cleanBuildDir()
        withBuildCache().run "weirdOutput"
        then:
        skipped ":weirdOutput"
        expectedOutput.file
    }

    def "local state declared via #api API is destroyed when task is loaded from cache"() {
        def localStateFile = file("local-state.json")
        buildFile << defineTaskWithLocalState(useRuntimeApi)

        when:
        withBuildCache().run "customTask"
        then:
        executedAndNotSkipped ":customTask"
        localStateFile.assertIsFile()

        when:
        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        skipped ":customTask"
        localStateFile.assertDoesNotExist()

        where:
        useRuntimeApi << [true, false]
        api = useRuntimeApi ? "runtime" : "annotation"
    }

    def "local state declared via #api API is not destroyed when task is not loaded from cache"() {
        def localStateFile = file("local-state.json")
        buildFile << defineTaskWithLocalState(useRuntimeApi)

        when:
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"
        localStateFile.assertIsFile()

        when:
        cleanBuildDir()
        succeeds "customTask", "-DassertLocalState=yes"
        then:
        executedAndNotSkipped ":customTask"

        where:
        useRuntimeApi << [true, false]
        api = useRuntimeApi ? "runtime" : "annotation"
    }

    def "null local state declared via #api API is supported"() {
        buildFile << defineTaskWithLocalState(useRuntimeApi, localStateFile)

        when:
        succeeds "customTask"
        then:
        executedAndNotSkipped ":customTask"

        where:
        useRuntimeApi | localStateFile
        true          | "{ null }"
        false         | "null"
        api = useRuntimeApi ? "runtime" : "annotation"
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @Issue("https://github.com/gradle/gradle/issues/3537")
    def "concurrent access to local cache works"() {
        def projectNames = GroovyCollections.combinations(('a'..'p'), ('a'..'p'), ('a'..'d'))*.join("")
        println "Running with ${projectNames.size()} projects"
        projectNames.each { projectName ->
            settingsFile << "include '$projectName'\n"
        }

        buildFile << """
            subprojects { project ->
                task test {
                    def outputFile = file("\${project.buildDir}/output.txt")
                    outputs.cacheIf { true }
                    outputs.file(outputFile).withPropertyName("outputFile")
                    doFirst {
                        Thread.sleep(new Random().nextInt(30))
                        outputFile.text = "output"
                    }
                }
            }
        """

        when:
        args "--parallel", "--max-workers=100"
        withBuildCache().run "test"
        then:
        noExceptionThrown()
    }

    @Requires({ GradleContextualExecuter.embedded })
    // this test only works in embedded mode because of the use of validation test fixtures
    def "invalid tasks are not cached"() {
        buildFile << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.integtests.fixtures.validation.ValidationProblem

            @CacheableTask
            abstract class InvalidTask extends DefaultTask {
                @ValidationProblem File input
                @OutputFile outputFile
                @TaskAction action() {
                    outputFile.text = "created"
                }
            }

            task invalid(type: InvalidTask) {
                input = file("input.txt")
                outputFile = file("build/output.txt")
            }
        """

        executer.beforeExecute {
            expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, dummyValidationProblem('InvalidTask', 'input'), 'id', 'section')
        }

        when:
        withBuildCache().run "invalid", "--info"
        then:
        outputContains("""|Caching disabled for task ':invalid' because:
            |  Caching has been disabled to ensure correctness. Please consult deprecation warnings for more details.
        """.stripMargin())
        executedAndNotSkipped(":invalid")
    }

    private static String defineCachedTask(String suffix = "") {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @OutputFile
                File outputFile

                @TaskAction
                void doSomething() {
                    outputFile.text = inputFile.text + "$suffix"
                }
            }
        """
    }

    private static String defineProducerTask() {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            abstract class ProducerTask extends DefaultTask {
                @InputFile @PathSensitive(PathSensitivity.NONE) File input
                @Optional @OutputFile nullFile
                @Optional @OutputDirectory nullDir
                @OutputFile File missingFile
                @OutputDirectory File missingDir
                @OutputFile File regularFile
                @OutputDirectory File emptyDir
                @OutputDirectory File singleFileInDir
                @OutputDirectory File manyFilesInDir
                @Inject abstract FileSystemOperations getFs()
                @TaskAction action() {
                    fs.delete {
                        delete(missingFile)
                        delete(missingDir)
                    }
                    regularFile.text = "regular file"
                    new File(singleFileInDir, "file.txt").text = "single file in dir"
                    new File(manyFilesInDir, "file-1.txt").text = "file #1 in dir"
                    new File(manyFilesInDir, "file-2.txt").text = "file #2 in dir"
                }
            }

            task producer(type: ProducerTask) {
                input = file("input.txt")
                missingFile = file("build/missing-file.txt")
                missingDir = file("build/missing-dir")
                regularFile = file("build/regular-file.txt")
                emptyDir = file("build/empty-dir")
                singleFileInDir = file("build/single-file-in-dir")
                manyFilesInDir = file("build/many-files-in-dir")
            }
        """
    }

    private static String defineConsumerTask() {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class ConsumerTask extends DefaultTask {
                @InputFile @PathSensitive(PathSensitivity.NONE) File regularFile
                @InputDirectory @PathSensitive(PathSensitivity.NONE) File emptyDir
                @InputDirectory @PathSensitive(PathSensitivity.NONE) File singleFileInDir
                @InputDirectory @PathSensitive(PathSensitivity.NONE) File manyFilesInDir
                @OutputFile File output
                @TaskAction action() {
                    output.text = "output"
                }
            }

            task consumer(type: ConsumerTask) {
                dependsOn producer
                regularFile = producer.regularFile
                emptyDir = producer.emptyDir
                singleFileInDir = producer.singleFileInDir
                manyFilesInDir = producer.manyFilesInDir
                output = file("build/output.txt")
            }
        """
    }

    private TestFile cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }

    void taskIsNotCached(String task) {
        withBuildCache().run task
        executedAndNotSkipped(task)
        cleanBuildDir()

        withBuildCache().run task
        executedAndNotSkipped(task)
    }

    void taskIsCached(String task) {
        withBuildCache().run task
        executedAndNotSkipped(task)
        cleanBuildDir()

        withBuildCache().run task
        skipped(task)
    }
}
