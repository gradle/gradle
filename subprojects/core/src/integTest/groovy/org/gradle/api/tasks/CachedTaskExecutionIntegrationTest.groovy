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

import org.gradle.caching.configuration.internal.DefaultBuildCacheConfiguration
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Ignore
import spock.lang.IgnoreIf

class CachedTaskExecutionIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {
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

    def setup() {
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
        withBuildCache().succeeds "jar"
        then:
        skippedTasks.empty

        expect:
        withBuildCache().succeeds "clean"

        when:
        withBuildCache().succeeds "jar"
        then:
        skippedTasks.containsAll ":compileJava"
    }

    // Note: this test only actually tests millisecond-precision when:
    //
    //   a) it is ran on a file system with finer-than-a-second time precision
    //      (only Ext4 and NTFS at the time of writing),
    //   b) the current Java implementation actually supports
    //      finer-than-a-second precision timestamps via File.lastModified()
    //      (only Windows Java 8 at the time of writing).
    //
    // Even when finer-than-a-millisecond precision would be available via
    // Files.getLastModifiedTime(), we still restore only millisecond precision
    // dates.
    def "restored cached results match original timestamp with millisecond precision"() {
        settingsFile << "rootProject.name = 'test'"
        withBuildCache().succeeds "jar"
        def classFile = file("build/classes/main/Hello.class")
        def originalModificationTime = classFile.assertIsFile().lastModified()

        when:
        // We really need to sleep here, and can't use the `makeOlder()` trick,
        // because the results are already cached with the original timestamp
        sleep(1000)
        withBuildCache().succeeds "clean"
        withBuildCache().succeeds "jar"

        then:
        skippedTasks.containsAll ":compileJava"
        classFile.lastModified() == originalModificationTime
    }

    def "cached tasks are executed with --rerun-tasks"() {
        expect:
        cacheDir.listFiles() as List == []

        when:
        withBuildCache().succeeds "jar"
        def originalCacheContents = listCacheFiles()
        def originalModificationTimes = originalCacheContents.collect { file -> TestFile.makeOlder(file); file.lastModified() }
        then:
        skippedTasks.empty
        originalCacheContents.size() > 0

        expect:
        withBuildCache().succeeds "clean"

        when:
        withBuildCache().succeeds "jar", "--rerun-tasks"
        def updatedCacheContents = listCacheFiles()
        def updatedModificationTimes = updatedCacheContents*.lastModified()
        then:
        nonSkippedTasks.containsAll ":compileJava", ":jar"
        updatedCacheContents == originalCacheContents
        originalModificationTimes.size().times { i ->
            assert originalModificationTimes[i] < updatedModificationTimes[i]
        }
    }

    def "task results don't get stored when pushing is disabled"() {
        settingsFile << """
            buildCache {
                local {
                    push = false
                }
            }
        """

        when:
        withBuildCache().succeeds "jar"
        then:
        skippedTasks.empty

        expect:
        withBuildCache().succeeds "clean"

        when:
        withBuildCache().succeeds "jar"
        then:
        nonSkippedTasks.containsAll ":compileJava", ":jar"
    }

    def "task results don't get loaded when pulling is disabled"() {
        expect:
        cacheDir.listFiles() as List == []

        when:
        withBuildCache().succeeds "jar"
        def originalCacheContents = listCacheFiles()
        def originalModificationTimes = originalCacheContents.collect { file -> TestFile.makeOlder(file); file.lastModified() }
        then:
        skippedTasks.empty
        originalCacheContents.size() > 0

        expect:
        withBuildCache().succeeds "clean"

        when:
        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()
        withBuildCache().succeeds "jar", "-D${DefaultBuildCacheConfiguration.BUILD_CACHE_CAN_PULL}=false"
        def updatedCacheContents = listCacheFiles()
        def updatedModificationTimes = updatedCacheContents*.lastModified()
        then:
        nonSkippedTasks.containsAll ":compileJava", ":jar"
        updatedCacheContents == originalCacheContents
        originalModificationTimes.size().times { i ->
            assert originalModificationTimes[i] < updatedModificationTimes[i]
        }
    }

    def "outputs are correctly loaded from cache"() {
        buildFile << """
            apply plugin: "application"
            mainClassName = "Hello"
        """
        withBuildCache().run "run"
        withBuildCache().run "clean"
        expect:
        withBuildCache().succeeds "run"
    }

    def "tasks get cached when source code changes without changing the compiled output"() {
        when:
        withBuildCache().succeeds "assemble"
        then:
        skippedTasks.empty

        file("src/main/java/Hello.java") << """
            // Change to source file without compiled result change
        """
        withBuildCache().succeeds "clean"

        when:
        withBuildCache().succeeds "assemble"
        then:
        nonSkippedTasks.contains ":compileJava"
    }

    def "tasks get cached when source code changes back to previous state"() {
        expect:
        withBuildCache().succeeds "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = CHANGED_HELLO_WORLD
        then:
        withBuildCache().succeeds "jar" assertTaskNotSkipped ":compileJava" assertTaskNotSkipped ":jar"

        when:
        file("src/main/java/Hello.java").text = ORIGINAL_HELLO_WORLD
        then:
        withBuildCache().succeeds "jar"
        result.assertTaskSkipped ":compileJava"
    }

    def "clean doesn't get cached"() {
        withBuildCache().run "assemble"
        withBuildCache().run "clean"
        withBuildCache().run "assemble"
        when:
        withBuildCache().succeeds "clean"
        then:
        nonSkippedTasks.contains ":clean"
    }

    def "task gets loaded from cache when it is executed from a different directory"() {
        // Compile Java in a different copy of the project
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir)

        when:
        executer.inDirectory(remoteProjectDir)
        remoteProjectDir.file("settings.gradle") << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = '${cacheDir.absoluteFile.toURI()}'
                }
            }
        """
        withBuildCache().succeeds "compileJava"
        then:
        skippedTasks.empty
        remoteProjectDir.file("build/classes/main/Hello.class").exists()

        // Remove the project completely
        remoteProjectDir.deleteDir()

        when:
        withBuildCache().succeeds "compileJava"
        then:
        skippedTasks.containsAll ":compileJava"
        file("build/classes/main/Hello.class").exists()
    }

    def "compile task gets loaded from cache when source is moved to another directory"() {
        def remoteProjectDir = file("remote-project")
        setupProjectInDirectory(remoteProjectDir, "other-than-main")

        when:
        executer.inDirectory(remoteProjectDir)
        remoteProjectDir.file("settings.gradle") << """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = '${cacheDir.absoluteFile.toURI()}'
                }
            }
        """
        withBuildCache().succeeds "compileJava"
        then:
        skippedTasks.empty
        remoteProjectDir.file("build/classes/main/Hello.class").exists()

        remoteProjectDir.deleteDir()

        when:
        withBuildCache().succeeds "compileJava"
        then:
        skippedTasks.containsAll ":compileJava"
        file("build/classes/main/Hello.class").exists()
    }

    @Ignore("Must fix for 4.0")
    def "using `doNotCacheIf` without reason is deprecated"() {
        given:
        buildFile << """
            task adHocTask {
                outputs.doNotCacheIf { true }
            }
        """

        when:
        executer.expectDeprecationWarning()
        withBuildCache().succeeds 'adHocTask'

        then:
        output.contains "The doNotCacheIf(Spec) method has been deprecated and is scheduled to be removed in Gradle 4.0. Please use the doNotCacheIf(String, Spec) method instead."
    }

    def "error message contains spec which failed to evaluate"() {
        given:
        buildFile << """
            task adHocTask {
                inputs.property("input") { true }
                outputs.file("someFile")
                outputs.cacheIf("on CI") { throw new RuntimeException() }
                doLast {
                    println "Success"
                }
            }
        """

        when:
        withBuildCache().fails 'adHocTask'

        then:
        errorOutput.contains("Could not evaluate spec for 'on CI'.")
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "can load twice from the cache with no changes"() {
        given:
        buildFile << """
            apply plugin: "application"
            mainClassName = "Hello"
        """

        when:
        withBuildCache().run 'clean', 'run'

        then:
        nonSkippedTasks.contains ':compileJava'

        when:
        withBuildCache().run 'clean', 'run'

        then:
        skippedTasks.contains ':compileJava'

        when:
        withBuildCache().run 'clean', 'run'

        then:
        skippedTasks.contains ':compileJava'
    }

    def "outputs loaded from the cache are snapshotted as outputs"() {
        buildFile << """ 
            apply plugin: 'base'

            task createOutput {
                def outputFile = file('build/output.txt')
                def inputFile = file('input.txt')
                inputs.file inputFile
                inputs.file 'unrelated-input.txt'
                outputs.file outputFile
                outputs.cacheIf { true }
                doLast {
                    if (!outputFile.exists() || outputFile.text != inputFile.text) {
                        outputFile.parentFile.mkdirs()
                        outputFile.text = file('input.txt').text
                    }
                }
            }
        """.stripIndent()

        def inputFile = file('input.txt')
        inputFile.text = "input text"
        def unrelatedInputFile = file('unrelated-input.txt')
        unrelatedInputFile.text = "not part of the input"
        def outputFile = file('build/output.txt')

        when:
        def taskPath = ':createOutput'
        withBuildCache().succeeds taskPath

        then:
        nonSkippedTasks.contains taskPath
        outputFile.text == "input text"

        when:
        succeeds 'clean'
        withBuildCache().succeeds taskPath

        then:
        skippedTasks.contains taskPath
        outputFile.text == "input text"

        when:
        unrelatedInputFile.text = "changed input"
        succeeds taskPath

        then:
        nonSkippedTasks.contains taskPath
        outputFile.text == "input text"

        when:
        outputFile.text = "that should not be the output"
        succeeds taskPath

        then:
        // If the output wouldn't have been captured then the task would be up to date
        nonSkippedTasks.contains taskPath
        outputFile.text == "input text"
    }

    def "no caching happens when local cache is disabled"() {
        settingsFile << """
            buildCache {
                local {
                    enabled = false
                }
            }
        """
        when:
        withBuildCache().succeeds "jar"
        then:
        skippedTasks.empty

        expect:
        withBuildCache().succeeds "clean"

        when:
        withBuildCache().succeeds "jar"
        then:
        skippedTasks.empty
    }
}
