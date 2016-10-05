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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import spock.lang.IgnoreIf

class CachedTaskExecutionIntegrationTest extends AbstractIntegrationSpec {
    public static final String ORIGINAL_HELLO_WORLD = """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """
    public static final String CHANGED_HELLO_WORLD = """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World with Changes!");
                }
            }
        """

    File cacheDir

    def setup() {
        // Make sure cache dir is empty for every test execution
        cacheDir = temporaryFolder.file("cache-dir").deleteDir().createDir()

        setupProjectInDirectory(testDirectory)
    }

    private static void setupProjectInDirectory(TestFile dir, String sourceDir = "main") {
        dir.file("build.gradle") << """
            apply plugin: "java"
        """

        dir.file("src/$sourceDir/java/Hello.java") << ORIGINAL_HELLO_WORLD
        dir.file("src/$sourceDir/resources/resource.properties") << """
            test=true
        """

        if (sourceDir != "main") {
            dir.file("build.gradle") << """
                sourceSets {
                    main {
                        java {
                            srcDir "src/$sourceDir/java"
                        }
                        resources {
                            srcDir "src/$sourceDir/resources"
                        }
                    }
                }
            """
        }
    }

    def "no task is re-executed when inputs are unchanged"() {
        when:
        succeedsWithCache "jar"
        then:
        skippedTasks.empty

        expect:
        succeedsWithCache "clean"

        when:
        succeedsWithCache "jar"
        then:
        skippedTasks.containsAll ":compileJava", ":jar"
    }

    def "cached tasks are executed with --rerun-tasks"() {
        expect:
        cacheDir.listFiles() as List == []

        when:
        succeedsWithCache "jar"
        def originalCacheContents = (cacheDir.listFiles() as List).sort()
        def originalModificationTimes = originalCacheContents.collect { file -> TestFile.makeOlder(file); file.lastModified() }
        then:
        skippedTasks.empty
        originalCacheContents.size() > 0

        expect:
        succeedsWithCache "clean"

        when:
        succeedsWithCache "jar", "--rerun-tasks"
        def updatedCacheContents = (cacheDir.listFiles() as List).sort()
        def updatedModificationTimes = updatedCacheContents*.lastModified()
        then:
        nonSkippedTasks.containsAll ":compileJava", ":jar"
        updatedCacheContents == originalCacheContents
        originalModificationTimes.size().times { i ->
            assert originalModificationTimes[i] < updatedModificationTimes[i]
        }
    }

    def "buildSrc is loaded from cache"() {
        file("buildSrc/src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*

            class MyTask extends DefaultTask {}
        """
        when:
        succeedsWithCache "jar"
        then:
        skippedTasks.empty

        expect:
        succeedsWithCache "clean"
        file("buildSrc/build").deleteDir()

        when:
        succeedsWithCache "jar"
        then:
        output.contains ":buildSrc:compileGroovy FROM-CACHE"
        output.contains ":buildSrc:jar FROM-CACHE"
    }

    def "tasks stay cached after buildSrc is rebuilt"() {
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
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        succeedsWithCache "jar", "customTask"
        then:
        skippedTasks.empty

        when:
        file("buildSrc/build").deleteDir()
        file("buildSrc/.gradle").deleteDir()
        // Run this without cache, so buildSrc gets rebuilt
        succeeds "clean"

        succeedsWithCache "jar", "customTask"
        then:
        skippedTasks.containsAll ":compileJava", ":jar", ":customTask"
    }

    def "changing custom task implementation in buildSrc doesn't invalidate built-in task"() {
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
        file("buildSrc/src/main/groovy/CustomTask.groovy").makeOlder()
        file("input.txt") << "input"
        buildFile << """
            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
        when:
        succeedsWithCache "jar", "customTask"
        then:
        skippedTasks.empty

        when:
        file("buildSrc/src/main/groovy/CustomTask.groovy").text = """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile File inputFile
                @OutputFile File outputFile
                @TaskAction void doSomething() {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = inputFile.text + ": modified"
                }
            }
        """

        run "clean"
        succeedsWithCache "jar", "customTask"
        then:
        skippedTasks.containsAll ":compileJava", ":jar"
        nonSkippedTasks.contains ":customTask"
    }

    def "outputs are correctly loaded from cache"() {
        buildFile << """
            apply plugin: "application"
            mainClassName = "Hello"
        """
        runWithCache "run"
        runWithCache "clean"
        expect:
        succeedsWithCache "run"
    }

    def "tasks get cached when source code changes without changing the compiled output"() {
        when:
        succeedsWithCache "assemble"
        then:
        skippedTasks.empty

        file("src/main/java/Hello.java") << """
            // Change to source file without compiled result change
        """
        succeedsWithCache "clean"

        when:
        succeedsWithCache "assemble"
        then:
        nonSkippedTasks.contains ":compileJava"
        skippedTasks.contains ":jar"
    }

    def "tasks get cached when source code changes back to previous state"() {
        expect:
        succeedsWithCache "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = CHANGED_HELLO_WORLD
        then:
        succeedsWithCache "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = ORIGINAL_HELLO_WORLD
        then:
        succeedsWithCache "jar"
        result.assertTaskSkipped ":compileJava"
        result.assertTaskSkipped ":jar"
    }

    def "jar tasks get cached even when output file is changed"() {
        file("settings.gradle") << "rootProject.name = 'test'"
        buildFile << """
            if (file("toggle.txt").exists()) {
                jar {
                    destinationDir = file("\$buildDir/other-jar")
                    baseName = "other-jar"
                }
            }
        """

        expect:
        succeedsWithCache "assemble"
        skippedTasks.empty
        file("build/libs/test.jar").isFile()

        succeedsWithCache "clean"
        !file("build/libs/test.jar").isFile()

        file("toggle.txt").touch()

        succeedsWithCache "assemble"
        skippedTasks.contains ":jar"
        !file("build/libs/test.jar").isFile()
        file("build/other-jar/other-jar.jar").isFile()
    }

    def "clean doesn't get cached"() {
        runWithCache "assemble"
        runWithCache "clean"
        runWithCache "assemble"
        when:
        succeedsWithCache "clean"
        then:
        nonSkippedTasks.contains ":clean"
    }

    def "task gets loaded from cache when it is executed from a different directory"() {
        // Compile Java in a different copy of the project
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir)

        when:
        executer.inDirectory(remoteProjectDir)
        succeedsWithCache "compileJava"
        then:
        skippedTasks.empty
        remoteProjectDir.file("build/classes/main/Hello.class").exists()

        // Remove the project completely
        remoteProjectDir.deleteDir()

        when:
        succeedsWithCache "compileJava"
        then:
        skippedTasks.containsAll ":compileJava"
        file("build/classes/main/Hello.class").exists()
    }

    def "compile task gets loaded from cache when source is moved to another directory"() {
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir, "other-than-main")

        when:
        executer.inDirectory(remoteProjectDir)
        succeedsWithCache "compileJava"
        then:
        skippedTasks.empty
        remoteProjectDir.file("build/classes/main/Hello.class").exists()

        remoteProjectDir.deleteDir()

        when:
        succeedsWithCache "compileJava"
        then:
        skippedTasks.containsAll ":compileJava"
        file("build/classes/main/Hello.class").exists()
    }

    def "cacheable task with cache disabled doesn't get cached"() {
        buildFile << """
            compileJava.outputs.cacheIf { false }
        """

        runWithCache "compileJava"
        runWithCache "clean"

        when:
        succeedsWithCache "compileJava"
        then:
        // :compileJava is not cached, but :jar is still cached as its inputs haven't changed
        nonSkippedTasks.contains ":compileJava"
    }

    def "cacheable task with multiple outputs doesn't get cached"() {
        buildFile << """
            compileJava.outputs.files files("output1.txt", "output2.txt")
            compileJava.doLast {
                file("output1.txt") << "data"
                file("output2.txt") << "data"
            }
        """

        runWithCache "compileJava"
        runWithCache "clean"

        when:
        succeedsWithCache "compileJava", "--info"
        then:
        // :compileJava is not cached, but :jar is still cached as its inputs haven't changed
        nonSkippedTasks.contains ":compileJava"
        output.contains "Not caching task ':compileJava' because it declares multiple output files for a single output property via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`"
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
            compileJava.dependsOn customTask
        """

        when:
        runWithCache "jar"
        then:
        nonSkippedTasks.contains ":customTask"

        when:
        runWithCache "clean"
        succeedsWithCache "jar"
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

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "can load twice from the cache with no changes"() {
        given:
        buildFile << """
            apply plugin: "application"
            mainClassName = "Hello"
        """

        when:
        runWithCache 'clean', 'run'

        then:
        nonSkippedTasks.contains ':compileJava'

        when:
        runWithCache 'clean', 'run'

        then:
        skippedTasks.contains ':compileJava'

        when:
        runWithCache 'clean', 'run'

        then:
        skippedTasks.contains ':compileJava'
    }

    def "task execution statistics are reported"() {
        given:
        // Force a forking executer
        // This is necessary since for the embedded executer
        // the Task statistics are not part of the output
        // returned by "this.output"
        executer.requireGradleDistribution()

        file("input.txt") << "data"
        buildFile << adHocTaskWithInputs()
        buildFile << """
            adHocTask { outputs.cacheIf { true } }

            task executedTask {
                doLast {
                    println 'Hello world'
                }
            }
        """

        when:
        runWithCache 'adHocTask', 'executedTask', 'compileJava'

        then:
        output.contains """
            3 tasks in build, out of which 3 (100%) were executed
            2  (67%) cache miss
            1  (33%) not cacheable
        """ .stripIndent()

        when:
        assert file('build/output.txt').delete()
        runWithCache 'adHocTask', 'executedTask', 'compileJava'

        then:
        output.contains """
            3 tasks in build, out of which 1 (33%) were executed
            1  (33%) up-to-date
            1  (33%) loaded from cache
            1  (33%) not cacheable
        """.stripIndent()
    }

    String adHocTaskWithInputs() {
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

    void taskIsNotCached(String task) {
        runWithCache task
        assert nonSkippedTasks.contains(task)
        runWithCache 'clean'

        runWithCache task
        assert nonSkippedTasks.contains(task)
    }

    void taskIsCached(String task) {
        runWithCache task
        assert nonSkippedTasks.contains(task)
        runWithCache 'clean'

        runWithCache task
        assert skippedTasks.contains(task)
    }

    def runWithCache(String... tasks) {
        enableCache()
        run tasks
    }

    def succeedsWithCache(String... tasks) {
        enableCache()
        succeeds tasks
    }

    private GradleExecuter enableCache() {
        executer.withArgument "-Dorg.gradle.cache.tasks=true"
        executer.withArgument "-Dorg.gradle.cache.tasks.directory=" + cacheDir.absolutePath
    }
}
