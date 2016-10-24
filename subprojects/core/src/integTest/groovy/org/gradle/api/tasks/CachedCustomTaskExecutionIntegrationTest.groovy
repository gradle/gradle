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
import org.gradle.integtests.fixtures.LocalTaskCacheFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.FIX_TO_WORK_ON_JAVA9
import static org.gradle.util.TestPrecondition.NOT_JDK_IBM

class CachedCustomTaskExecutionIntegrationTest extends AbstractIntegrationSpec implements LocalTaskCacheFixture {
    public static final String APPLY_KOTLIN = """
            plugins {
                id "nebula.kotlin" version "1.0.4"
            }
            repositories {
                // required to resolve kotlin-stdlib dependency implicitly added by nebula.kotlin
                jcenter()
            }
            dependencies {
                compile(gradleApi())
            }
        """

    def "buildSrc is loaded from cache"() {
        file("buildSrc/src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*

            class MyTask extends DefaultTask {}
        """
        when:
        withCache().succeeds "tasks"
        then:
        skippedTasks.empty

        expect:
        file("buildSrc/build").assertIsDir().deleteDir()

        when:
        withCache().succeeds "tasks"
        then:
        output.contains ":buildSrc:compileGroovy FROM-CACHE"
        output.contains ":buildSrc:jar FROM-CACHE"
    }

    def "tasks stay cached after buildSrc with custom Groovy task is rebuilt"() {
        file("buildSrc/src/main/groovy/CustomTask.groovy") << customGroovyTask()
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        withCache().succeeds "customTask"
        then:
        skippedTasks.empty

        when:
        file("buildSrc/build").deleteDir()
        file("buildSrc/.gradle").deleteDir()
        cleanBuildDir()

        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
    }

    @Issue("https://github.com/gradle/gradle-script-kotlin/issues/154")
    @Requires([FIX_TO_WORK_ON_JAVA9, NOT_JDK_IBM])
    def "tasks stay cached after buildSrc with custom Kotlin task is rebuilt"() {
        file("buildSrc/src/main/kotlin/CustomTask.kt") << customKotlinTask()
        file("buildSrc/build.gradle") << APPLY_KOTLIN
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        withCache().succeeds "customTask"
        then:
        skippedTasks.empty

        when:
        file("buildSrc/build").deleteDir()
        file("buildSrc/.gradle").deleteDir()
        cleanBuildDir()

        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
    }

    def "changing custom Groovy task implementation in buildSrc doesn't invalidate built-in task"() {
        def taskSourceFile = file("buildSrc/src/main/groovy/CustomTask.groovy")
        taskSourceFile << customGroovyTask()
        taskSourceFile.makeOlder()
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        withCache().succeeds "customTask"
        then:
        skippedTasks.empty
        file("build/output.txt").text == "input"

        when:
        taskSourceFile.text = customGroovyTask(" modified")

        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "input modified"
    }

    @Issue("https://github.com/gradle/gradle-script-kotlin/issues/154")
    @Requires([FIX_TO_WORK_ON_JAVA9, NOT_JDK_IBM])
    def "changing custom Kotlin task implementation in buildSrc doesn't invalidate built-in task"() {
        def taskSourceFile = file("buildSrc/src/main/kotlin/CustomTask.kt")
        taskSourceFile << customKotlinTask()
        taskSourceFile.makeOlder()
        file("buildSrc/build.gradle") << APPLY_KOTLIN
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        withCache().succeeds "customTask"
        then:
        skippedTasks.empty
        file("build/output.txt").text == "input"

        when:
        taskSourceFile.text = customKotlinTask(" modified")

        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "input modified"
    }

    private static String customGroovyTask(String suffix = "") {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile File inputFile
                @OutputFile File outputFile
                @TaskAction void doSomething() {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = inputFile.text + "$suffix"
                }
            }
        """
    }

    private static String customKotlinTask(String suffix = "") {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import java.io.File

            @CacheableTask
            open class CustomTask() : DefaultTask() {
                @get:InputFile var inputFile: File? = null
                @get:OutputFile var outputFile: File? = null
                @TaskAction fun doSomething() {
                    outputFile!!.parentFile.mkdirs()
                    outputFile!!.writeText(inputFile!!.readText())
                    outputFile!!.appendText("$suffix")
                }
            }
        """
    }

    def "cacheable task with cache disabled doesn't get cached"() {
        file("input.txt") << "data"
        file("buildSrc/src/main/groovy/CustomTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile File inputFile
                @OutputFile File outputFile
                @TaskAction void doSomething() {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = inputFile.text
                }
            }
        """
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file("input.txt")
                outputFile = file("\$buildDir/output.txt")
            }
        """

        when:
        withCache().run "customTask"
        then:
        nonSkippedTasks.contains ":customTask"

        when:
        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"


        when:
        buildFile << """
            customTask.outputs.cacheIf { false }
        """

        withCache().run "customTask"
        cleanBuildDir()

        withCache().succeeds "customTask"

        then:
        nonSkippedTasks.contains ":customTask"
    }

    def "cacheable task with multiple outputs doesn't get cached"() {
        buildFile << """
            task customTask {
                outputs.cacheIf { true }
                outputs.files files("build/output1.txt", "build/output2.txt")
                doLast {
                    file("build").mkdirs()
                    file("build/output1.txt") << "data"
                    file("build/output2.txt") << "data"
                }
            }
        """

        when:
        withCache().succeeds "customTask", "--info"
        then:
        nonSkippedTasks.contains ":customTask"
        output.contains "Not caching task ':customTask' because it declares multiple output files for a single output property via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`"
    }

    def "non-cacheable task with cache enabled gets cached"() {
        file("input.txt") << "data"
        buildFile << """
            class NonCacheableTask extends DefaultTask {
                @InputFile inputFile
                @OutputFile outputFile

                @TaskAction copy() {
                    project.mkdir outputFile.parentFile
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
        withCache().run "customTask"
        then:
        nonSkippedTasks.contains ":customTask"

        when:
        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
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
            def outputFile = file("\$buildDir/output.txt")
            inputs.file(file("input.txt"))
            outputs.file(outputFile)
            doLast {
                project.mkdir outputFile.parentFile
                outputFile.text = file("input.txt").text
            }
        }
        """.stripIndent()
    }

    def "optional file output is not stored when there is no output"() {
        file("input.txt") << "data"
        file("buildSrc/src/main/groovy/CustomTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile File inputFile
                @OutputFile File outputFile
                @Optional @OutputFile File secondaryOutputFile
                @TaskAction void doSomething() {
                    outputFile.parentFile.mkdirs()
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
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build/secondary.txt").text == "secondary"
        file("build").listFiles().sort() as List == [file("build/output.txt"), file("build/secondary.txt")]

        when:
        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build/secondary.txt").text == "secondary"
        file("build").listFiles().sort() as List == [file("build/output.txt"), file("build/secondary.txt")]

        when:
        cleanBuildDir()
        buildFile << """
            customTask.secondaryOutputFile = null
        """
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build").listFiles().sort() as List == [file("build/output.txt")]

        when:
        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build").listFiles().sort() as List == [file("build/output.txt")]
    }

    def "plural output files are only restored when map keys match"() {
        file("input.txt") << "data"
        file("buildSrc/src/main/groovy/CustomTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile File inputFile
                @OutputFiles Map<String, File> outputFiles
                @TaskAction void doSomething() {
                    outputFiles.each { String key, File outputFile ->
                        outputFile.parentFile.mkdirs()
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
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
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
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
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
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output-a.txt").text == "first"
        file("build/output-b.txt").text == "second"
        file("build").listFiles().sort() as List == [file("build/output-a.txt"), file("build/output-b.txt")]
    }

    @Unroll
    def "missing output #type is not cached"() {
        given:
        file("input.txt") << "data"
        buildFile << """
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output.txt" withPropertyName "output"
                outputs.$type "build/missing" withPropertyName "missing"
                outputs.cacheIf { true }
                doLast {
                    file("build").mkdirs()
                    file("build/output.txt").text = file("input.txt").text
                }
            }
        """

        when:
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build/missing").assertDoesNotExist()

        when:
        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build/missing").assertDoesNotExist()

        where:
        type   | _
        "file" | _
        // TODO:LPTR Do not create output directory when output directory
        // was not present at original execution
        // "dir"  | _
    }

    def "empty output directory is cached properly"() {
        given:
        file("input.txt") << "data"
        buildFile << """
            task customTask {
                inputs.file "input.txt"
                outputs.file "build/output.txt" withPropertyName "output"
                outputs.dir "build/empty" withPropertyName "empty"
                outputs.cacheIf { true }
                doLast {
                    file("build/empty").mkdirs()
                    file("build/output.txt").text = file("input.txt").text
                }
            }
        """

        when:
        withCache().succeeds "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build/empty").assertIsEmptyDir()

        when:
        cleanBuildDir()
        withCache().succeeds "customTask"
        then:
        skippedTasks.contains ":customTask"
        file("build/output.txt").text == "data"
        file("build/empty").assertIsEmptyDir()
    }

    private TestFile cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }

    void taskIsNotCached(String task) {
        withCache().run task
        assert nonSkippedTasks.contains(task)
        cleanBuildDir()

        withCache().run task
        assert nonSkippedTasks.contains(task)
    }

    void taskIsCached(String task) {
        withCache().run task
        assert nonSkippedTasks.contains(task)
        cleanBuildDir()

        withCache().run task
        assert skippedTasks.contains(task)
    }
}
