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

package org.gradle.internal.watch

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.watch.registry.impl.WatchableHierarchies
import spock.lang.Issue

@LocalOnly
class ChangesBetweenBuildsFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def "source file changes are recognized"() {
        buildFile << """
            apply plugin: "application"

            application.mainClass = "Main"
        """

        def mainSourceFileRelativePath = "src/main/java/Main.java"
        def mainSourceFile = file(mainSourceFileRelativePath)
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        runWithWatchingEnabled("run")
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        waitForChangesToBePickedUp()
        runWithWatchingEnabled "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    def "buildSrc changes are recognized"() {
        def taskSourceFile = file("buildSrc/src/main/java/PrinterTask.java")
        taskSourceFile.text = taskWithGreeting("Hello from original task!")

        buildFile << """
            task hello(type: PrinterTask)
        """

        when:
        runWithWatchingEnabled "hello"
        then:
        outputContains "Hello from original task!"

        when:
        taskSourceFile.text = taskWithGreeting("Hello from modified task!")
        waitForChangesToBePickedUp()
        runWithWatchingEnabled "hello"
        then:
        outputContains "Hello from modified task!"
    }

    def "Groovy build script changes are recognized"() {
        when:
        buildFile.text = """
            println "Hello from the build!"
        """
        runWithWatchingEnabled "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildFile.text = """
            println "Hello from the modified build!"
        """
        runWithWatchingEnabled "help"
        then:
        outputContains "Hello from the modified build!"
    }

    def "Kotlin build script changes are recognized"() {
        when:
        buildKotlinFile.text = """
            println("Hello from the build!")
        """
        runWithWatchingEnabled "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildKotlinFile.text = """
            println("Hello from the modified build!")
        """
        runWithWatchingEnabled "help"
        then:
        outputContains "Hello from the modified build!"
    }

    def "settings script changes are recognized"() {
        when:
        settingsFile.text = """
            println "Hello from settings!"
        """
        runWithWatchingEnabled "help"
        then:
        outputContains "Hello from settings!"

        when:
        settingsFile.text = """
            println "Hello from modified settings!"
        """
        runWithWatchingEnabled "help"
        then:
        outputContains "Hello from modified settings!"
    }

    def "source file changes are recognized when retention has just been enabled"() {
        buildFile << """
            apply plugin: "application"

            application.mainClass = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        runWithWatchingDisabled "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        runWithWatchingEnabled "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    def "source file changes are recognized when retention has just been disabled"() {
        buildFile << """
            apply plugin: "application"

            application.mainClass = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        runWithWatchingEnabled "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        runWithWatchingDisabled "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    @Issue("https://github.com/gradle/gradle/issues/17865")
    def "#description directory moved is handled properly"() {
        def buildCache = new TestBuildCache(temporaryFolder.file("cache-dir").deleteDir().createDir())
        def buildFileContents = """
            apply plugin: "base"

            @CacheableTask
            abstract class CustomTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                abstract RegularFileProperty getSource()

                @OutputFile
                abstract RegularFileProperty getTarget()

                @TaskAction
                def execute() {
                    target.get().asFile.text = source.get().asFile.text
                }
            }

            tasks.register("run", CustomTask) {
                source = file("source.txt")
                target = file("build/target.txt")
            }
        """

        def originalDir = file("original")
        def renamedDir = file("renamed")
        def projectDir = originalDir.file(*projectDirPath)
        def settingsFile = projectDir.file("settings.gradle")
        def buildFile = projectDir.file("build.gradle")
        def sourceFile = projectDir.file("source.txt")
        def targetFile = projectDir.file("build/target.txt")

        executer.beforeExecute {
            inDirectory(projectDir)
            enableVerboseVfsLogs()
            withBuildCache()
            withWatchFs()
        }

        settingsFile.text = buildCache.localCacheConfiguration()
        buildFile.text = buildFileContents
        sourceFile.text = "Hello Old World!"

        when:
        runWithWatchingEnabled "run"
        then:
        targetFile.text == "Hello Old World!"
        executedAndNotSkipped ":run"

        expect:
        succeeds "clean"

        when:
        originalDir.renameTo(renamedDir)
        settingsFile.text = buildCache.localCacheConfiguration()
        buildFile.text = buildFileContents
        sourceFile.text = "Hello New World!"

        runWithWatchingEnabled "run"
        then:
        targetFile.text == "Hello New World!"
        executedAndNotSkipped ":run"

        where:
        description      | projectDirPath
        'project'        | []
        'parent project' | ['my/project/dir']
    }

    ExecutionResult runWithWatchingEnabled(String... tasks) {
        def result = withWatchFs().run tasks
        assertOutputContainsNoInvalidation()
        return result
    }

    ExecutionResult runWithWatchingDisabled(String... tasks) {
        def result = withoutWatchFs().run tasks
        assertOutputContainsNoInvalidation()
        return result
    }

    void assertOutputContainsNoInvalidation() {
        outputDoesNotContain(WatchableHierarchies.INVALIDATING_HIERARCHY_MESSAGE)
    }
}
