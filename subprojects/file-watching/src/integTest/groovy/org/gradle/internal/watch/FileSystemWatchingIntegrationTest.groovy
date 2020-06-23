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

import com.google.common.collect.ImmutableSet
import org.apache.commons.io.FileUtils
import org.gradle.cache.GlobalCacheLocations
import org.gradle.initialization.StartParameterBuildOptions.WatchFileSystemOption
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.FileSystemWatchingFixture
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

// The whole test makes no sense if there isn't a daemon to retain the state.
@IgnoreIf({ GradleContextualExecuter.noDaemon || GradleContextualExecuter.watchFs })
@Unroll
class FileSystemWatchingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, FileSystemWatchingFixture {
    private static final String INCUBATING_MESSAGE = "Watching the file system is an incubating feature"

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        // Make the first build in each test drop the VFS state
        executer.requireIsolatedDaemons()
    }

    def "source file changes are recognized"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFileRelativePath = "src/main/java/Main.java"
        def mainSourceFile = file(mainSourceFileRelativePath)
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withWatchFs().run "run", "--info"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
        assertWatchedRootDirectories([ImmutableSet.of(testDirectory)])

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        waitForChangesToBePickedUp()
        withWatchFs().run "run", "--info"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
        assertWatchedRootDirectories([ImmutableSet.of(testDirectory)])
    }

    def "buildSrc changes are recognized"() {
        def taskSourceFile = file("buildSrc/src/main/java/PrinterTask.java")
        taskSourceFile.text = taskWithGreeting("Hello from original task!")

        buildFile << """
            task hello(type: PrinterTask)
        """

        when:
        withWatchFs().run "hello", "--info"
        then:
        outputContains "Hello from original task!"
        assertWatchedRootDirectories([ImmutableSet.of(testDirectory)] * 2)

        when:
        taskSourceFile.text = taskWithGreeting("Hello from modified task!")
        waitForChangesToBePickedUp()
        withWatchFs().run "hello", "--info"
        then:
        outputContains "Hello from modified task!"
        assertWatchedRootDirectories([ImmutableSet.of(testDirectory)] * 2)
    }

    def "Groovy build script changes get recognized"() {
        when:
        buildFile.text = """
            println "Hello from the build!"
        """
        withWatchFs().run "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildFile.text = """
            println "Hello from the modified build!"
        """
        withWatchFs().run "help"
        then:
        outputContains "Hello from the modified build!"
    }

    def "Kotlin build script changes get recognized"() {
        when:
        buildKotlinFile.text = """
            println("Hello from the build!")
        """
        withWatchFs().run "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildKotlinFile.text = """
            println("Hello from the modified build!")
        """
        withWatchFs().run "help"
        then:
        outputContains "Hello from the modified build!"
    }

    def "settings script changes get recognized"() {
        when:
        settingsFile.text = """
            println "Hello from settings!"
        """
        withWatchFs().run "help"
        then:
        outputContains "Hello from settings!"

        when:
        settingsFile.text = """
            println "Hello from modified settings!"
        """
        withWatchFs().run "help"
        then:
        outputContains "Hello from modified settings!"
    }

    def "source file changes are recognized when retention has just been enabled"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withoutWatchFs().run "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withWatchFs().run "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    def "source file changes are recognized when retention has just been disabled"() {
        buildFile << """
            apply plugin: "application"

            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withWatchFs().run "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withoutWatchFs().run "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    @ToBeFixedForInstantExecution(because = "2 more files retained")
    def "detects input file change just before the task is executed"() {
        executer.requireDaemon()
        server.start()

        def inputFile = file("input.txt")
        buildFile << """
            def inputFile = file("input.txt")
            def outputFile = file("build/output.txt")

            task waitForUserChanges {
                doLast {
                    ${server.callFromBuild("userInput")}
                }
            }

            task consumer {
                inputs.file(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = inputFile.text
                }
                dependsOn(waitForUserChanges)
            }
        """

        when:
        runWithRetentionAndDoChangesWhen("consumer", "userInput") {
            inputFile.text = "initial"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        // TODO: sometimes, the changes from the same build are picked up
        retainedFilesInCurrentBuild >= 1

        when:
        runWithRetentionAndDoChangesWhen("consumer", "userInput") {
            inputFile.text = "changed"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        receivedFileSystemEventsInCurrentBuild >= 1
        retainedFilesInCurrentBuild == 10 // 8 build script class files + 2 task files
    }

    @ToBeFixedForInstantExecution(because = "2 more files retained")
    def "detects input file change after the task has been executed"() {
        executer.requireDaemon()
        server.start()

        def inputFile = file("input.txt")
        def outputFile = file("build/output.txt")
        buildFile << """
            def inputFile = file("input.txt")
            def outputFile = file("build/output.txt")

            task consumer {
                inputs.file(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = inputFile.text
                }
            }

            task waitForUserChanges {
                dependsOn(consumer)
                doLast {
                    ${server.callFromBuild("userInput")}
                }
            }
        """

        when:
        inputFile.text = "initial"
        runWithRetentionAndDoChangesWhen("waitForUserChanges", "userInput") {
            inputFile.text = "changed"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "initial"
        retainedFilesInCurrentBuild == 9 // 8 script classes + 1 task file

        when:
        runWithRetentionAndDoChangesWhen("waitForUserChanges", "userInput") {
            inputFile.text = "changedAgain"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "changed"
        receivedFileSystemEventsInCurrentBuild >= 1
        retainedFilesInCurrentBuild == 9 // 8 script classes + 1 task file

        when:
        server.expect("userInput")
        withWatchFs().run("waitForUserChanges")
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "changedAgain"
        retainedFilesInCurrentBuild == 10 // 8 script classes + 2 task files
    }

    private void runWithRetentionAndDoChangesWhen(String task, String expectedCall, Closure action) {
        def handle = withWatchFs().executer.withTasks(task).start()
        def userInput = server.expectAndBlock(expectedCall)
        userInput.waitForAllPendingCalls()
        action()
        userInput.releaseAll()
        result = handle.waitForFinish()
    }

    @ToBeFixedForInstantExecution(because = "composite build not yet supported")
    @Requires(TestPrecondition.NOT_WINDOWS) // https://github.com/gradle/gradle-private/issues/3116
    def "works with composite build"() {
        buildTestFixture.withBuildInSubDir()
        def includedBuild = singleProjectBuild("includedBuild") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def consumer = singleProjectBuild("consumer") {
            buildFile << """
                apply plugin: 'java'

                dependencies {
                    implementation "org.test:includedBuild:1.0"
                }
            """
            settingsFile << """
                includeBuild("../includedBuild")
            """
        }
        executer.beforeExecute {
            inDirectory(consumer)
        }
        def expectedBuildRootDirectories = [
            ImmutableSet.of(consumer),
            ImmutableSet.of(consumer, includedBuild)
        ]

        when:
        withWatchFs().run "assemble", "--info"
        then:
        executedAndNotSkipped(":includedBuild:jar")
        assertWatchedRootDirectories(expectedBuildRootDirectories)

        when:
        withWatchFs().run("assemble", "--info")
        then:
        skipped(":includedBuild:jar")
        assertWatchedRootDirectories(expectedBuildRootDirectories)

        when:
        includedBuild.file("src/main/java/NewClass.java")  << "public class NewClass {}"
        withWatchFs().run("assemble")
        then:
        executedAndNotSkipped(":includedBuild:jar")
    }

    @Requires(TestPrecondition.NOT_WINDOWS) // https://github.com/gradle/gradle-private/issues/3116
    @ToBeFixedForInstantExecution(because = "GradleBuild task is not yet supported")
    def "works with GradleBuild task"() {
        buildTestFixture.withBuildInSubDir()
        def buildInBuild = singleProjectBuild("buildInBuild") {
            buildFile << """
                apply plugin: 'java'
            """
        }
        def consumer = singleProjectBuild("consumer") {
            buildFile << """
                apply plugin: 'java'

                task buildInBuild(type: GradleBuild) {
                    startParameter.currentDir = file('../buildInBuild')
                }
            """
        }
        executer.beforeExecute {
            inDirectory(consumer)
        }
        def expectedBuildRootDirectories = [
            ImmutableSet.of(consumer),
            ImmutableSet.of(consumer, buildInBuild)
        ]

        when:
        withWatchFs().run "buildInBuild", "--info"
        then:
        assertWatchedRootDirectories(expectedBuildRootDirectories)

        when:
        withWatchFs().run "buildInBuild", "--info"
        then:
        assertWatchedRootDirectories(expectedBuildRootDirectories)
    }

    def "incubating message is shown for watching the file system"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        withWatchFs().run("assemble")
        then:
        outputContains(INCUBATING_MESSAGE)

        when:
        withoutWatchFs().run("assemble")
        then:
        outputDoesNotContain(INCUBATING_MESSAGE)
    }

    def "can be enabled via gradle.properties"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        file("gradle.properties") << "${WatchFileSystemOption.GRADLE_PROPERTY}=true"
        run("assemble")
        then:
        outputContains(INCUBATING_MESSAGE)
    }

    def "can be enabled via #commandLineOption"() {
        buildFile << """
            apply plugin: "java"
        """

        when:
        run("assemble", commandLineOption)
        then:
        outputContains(INCUBATING_MESSAGE)

        where:
        commandLineOption << ["-D${WatchFileSystemOption.GRADLE_PROPERTY}=true", "--watch-fs"]
    }

    def "deprecation message is shown when using the old property to enable watching the file system"() {
        buildFile << """
            apply plugin: "java"
        """
        executer.expectDocumentedDeprecationWarning(
            "Using the system property org.gradle.unsafe.vfs.retention to enable watching the file system has been deprecated. " +
                "This is scheduled to be removed in Gradle 7.0. " +
                "Use the gradle property org.gradle.unsafe.watch-fs instead. " +
                "See https://docs.gradle.org/current/userguide/gradle_daemon.html for more details."
        )

        expect:
        succeeds("assemble", "-D${VirtualFileSystemServices.DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY}=true")
    }

    def "detects when outputs are removed for tasks without sources"() {
        buildFile << """
            apply plugin: 'base'

            abstract class Producer extends DefaultTask {
                @InputDirectory
                @SkipWhenEmpty
                abstract DirectoryProperty getSources()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void listSources() {
                    outputDir.file("output.txt").get().asFile.text = sources.get().asFile.list().join("\\n")
                }
            }

            abstract class Consumer extends DefaultTask {
                @InputFiles
                abstract DirectoryProperty getInputDirectory()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void run() {
                    def input = inputDirectory.file("output.txt").get().asFile
                    if (input.file) {
                        outputFile.asFile.get().text = input.text
                    } else {
                        outputFile.asFile.get().text = "<empty>"
                    }
                }
            }

            task sourceTask(type: Producer) {
                sources = file("sources")
                outputDir = file("build/output")
            }

            task consumer(type: Consumer) {
                inputDirectory = sourceTask.outputDir
                outputFile = file("build/consumer.txt")
            }
        """

        def sourcesDir = file("sources")
        def sourceFile = sourcesDir.file("source.txt").createFile()
        def outputFile = file("build/output/output.txt")

        when:
        withWatchFs().run ":consumer"
        then:
        executedAndNotSkipped(":sourceTask", ":consumer")
        outputFile.assertExists()

        when:
        sourceFile.delete()
        waitForChangesToBePickedUp()
        withWatchFs().run ":consumer"
        then:
        executedAndNotSkipped(":sourceTask", ":consumer")
        outputFile.assertDoesNotExist()
    }

    def "detects when stale outputs are removed"() {
        buildFile << """
            apply plugin: 'base'

            task producer {
                def inputTxt = file("input.txt")
                def outputTxt = file("build/output.txt")
                inputs.files(inputTxt)
                outputs.file(outputTxt)
                doLast {
                    outputTxt.text = inputTxt.text
                }
            }
        """

        file("input.txt").text = "input"
        def outputFile = file("build/output.txt")

        when:
        withWatchFs().run ":producer"
        then:
        executedAndNotSkipped(":producer")
        outputFile.assertExists()

        when:
        invalidateBuildOutputCleanupState()
        waitForChangesToBePickedUp()
        withWatchFs().run ":producer", "--info"
        then:
        output.contains("Deleting stale output file: ${outputFile.absolutePath}")
        executedAndNotSkipped(":producer")
        outputFile.assertExists()
    }

    def "detects non-incremental cleanup of incremental tasks"() {
        buildFile << """
            abstract class IncrementalTask extends DefaultTask {
                @InputDirectory
                @Incremental
                abstract DirectoryProperty getSources()

                @Input
                abstract Property<String> getInput()

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void processChanges(InputChanges changes) {
                    outputDir.file("output.txt").get().asFile.text = input.get()
                }
            }

            task incremental(type: IncrementalTask) {
                sources = file("sources")
                input = providers.systemProperty("outputDir").forUseAtConfigurationTime()
                outputDir = file("build/\${input.get()}")
            }
        """

        file("sources/input.txt").text = "input"

        when:
        withWatchFs().run ":incremental", "-DoutputDir=output1"
        then:
        executedAndNotSkipped(":incremental")

        when:
        file("build/output2/overlapping.txt").text = "overlapping"
        waitForChangesToBePickedUp()
        withWatchFs().run ":incremental", "-DoutputDir=output2"
        then:
        executedAndNotSkipped(":incremental")
        file("build/output1").assertDoesNotExist()

        when:
        waitForChangesToBePickedUp()
        withWatchFs().run ":incremental", "-DoutputDir=output1"
        then:
        executedAndNotSkipped(":incremental")
        file("build/output1").assertExists()
    }

    def "detects changes to manifest"() {
        buildFile << """
            plugins {
                id 'java'
            }

            jar {
                manifest {
                    attributes('Created-By': providers.systemProperty("creator"))
                }
            }
        """

        when:
        withWatchFs().run "jar", "-Dcreator=first"
        then:
        file("build/tmp/jar/MANIFEST.MF").text.contains("first")
        executedAndNotSkipped(":jar")

        when:
        withWatchFs().run "jar", "-Dcreator=second"
        then:
        file("build/tmp/jar/MANIFEST.MF").text.contains("second")
        executedAndNotSkipped(":jar")
    }

    def "detects when local state is removed"() {
        buildFile << """
            plugins {
                id 'base'
            }

            @CacheableTask
            abstract class WithLocalStateDirectory extends DefaultTask {
                @LocalState
                abstract DirectoryProperty getLocalStateDirectory()
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    def localStateDir = localStateDirectory.get().asFile
                    localStateDir.mkdirs()
                    new File(localStateDir, "localState.txt").text = "localState"
                    outputFile.get().asFile.text = "output"
                }
            }

            abstract class WithOutputFile extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void doStuff() {
                    outputFile.get().asFile.text = "outputFile"
                }
            }

            task localStateTask(type: WithLocalStateDirectory) {
                localStateDirectory = file("build/overlap")
                outputFile = file("build/localStateOutput.txt")
            }

            task outputFileTask(type: WithOutputFile) {
                outputFile = file("build/overlap/outputFile.txt")
            }
        """
        def localStateOutputFile = file("build/localStateOutput.txt")

        when:
        withWatchFs().withBuildCache().run("localStateTask", "outputFileTask")
        then:
        executedAndNotSkipped(":localStateTask", ":outputFileTask")
        localStateOutputFile.exists()

        when:
        localStateOutputFile.delete()
        waitForChangesToBePickedUp()
        withWatchFs().withBuildCache().run("localStateTask", "outputFileTask")
        then:
        skipped(":localStateTask")
        executedAndNotSkipped(":outputFileTask")
    }

    def "gracefully handle the root project not being available"() {
        settingsFile << """
            throw new RuntimeException("Boom")
        """

        when:
        withWatchFs().fails("help")
        then:
        failureHasCause("Boom")
    }

    def "root project dir does not need to exist"() {
        def settingsDir = file("gradle")
        def settingsFile = settingsDir.file("settings.gradle")
        settingsFile << """
            rootProject.projectDir = new File(settingsDir, '../root')
            include 'sub'
            project(':sub').projectDir = new File(settingsDir, '../sub')
        """
        file("sub/build.gradle") << "task thing"

        when:
        inDirectory(settingsDir)
        withWatchFs().run("thing")
        then:
        executed ":sub:thing"

    }

    @Issue("https://github.com/gradle/gradle/issues/11851")
    @Requires(TestPrecondition.SYMLINKS)
    def "gracefully handle when declaring the same path as an input via symlinks"() {
        def actualDir = file("actualDir").createDir()
        file("symlink1").createLink(actualDir)
        file("symlink2").createLink(actualDir)

        buildFile << """
            task myTask {
                def outputFile = file("build/output.txt")
                inputs.dir("symlink1")
                inputs.dir("symlink2")
                outputs.file(outputFile)

                doLast {
                    outputFile.text = "Hello world"
                }
            }
        """

        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        withWatchFs().run "myTask"
        then:
        skipped(":myTask")
    }

    @Issue("https://github.com/gradle/gradle/issues/11851")
    @Requires(TestPrecondition.SYMLINKS)
    def "changes to #description are detected"() {
        file(fileToChange).createFile()
        file(linkSource).createLink(file(linkTarget))

        buildFile << """
            task myTask {
                def outputFile = file("build/output.txt")
                inputs.${inputDeclaration}
                outputs.file(outputFile)

                doLast {
                    outputFile.text = "Hello world"
                }
            }
        """

        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"

        when:
        withWatchFs().run "myTask"
        then:
        skipped(":myTask")

        when:
        file(fileToChange).text = "changed"
        waitForChangesToBePickedUp()
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped ":myTask"

        where:
        description                     | linkSource                     | linkTarget       | inputDeclaration        | fileToChange
        "symlinked file"                | "symlinkedFile"                | "actualFile"     | 'file("symlinkedFile")' | "actualFile"
        "symlinked directory"           | "symlinkedDir"                 | "actualDir"      | 'dir("symlinkedDir")'   | "actualDir/file.txt"
        "symlink in a directory"        | "dirWithSymlink/symlinkInside" | "fileInside.txt" | 'dir("dirWithSymlink")' | "fileInside.txt"
    }

    @Unroll
    def "detects when a task removes the build directory #buildDir"() {
        buildFile << """
            apply plugin: 'base'

            project.buildDir = file("${buildDir}")

            task myClean {
                doLast {
                    delete buildDir
                }
            }

            task producer {
                def outputFile = new File(buildDir, "some/file/in/buildDir/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "Output"
                }
            }
        """

        when:
        withWatchFs().run "producer"
        then:
        executedAndNotSkipped ":producer"

        when:
        withWatchFs().run "myClean"
        withWatchFs().run "producer"
        then:
        executedAndNotSkipped ":producer"

        where:
        buildDir << ["build", "build/myProject"]
    }

    @Issue("https://github.com/gradle/gradle/issues/12614")
    def "can remove watched directory after all files inside have been removed"() {
        // This test targets Windows, where watched directories can't be deleted.

        def projectDir = file("projectDir")
        projectDir.file("build.gradle") << """
            apply plugin: "java-library"
        """
        projectDir.file("settings.gradle").createFile()

        def mainSourceFile = projectDir.file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        inDirectory(projectDir)
        withWatchFs().run "assemble"
        then:
        executedAndNotSkipped ":assemble"

        when:
        FileUtils.cleanDirectory(projectDir)
        waitForChangesToBePickedUp()
        then:
        projectDir.delete()
    }

    def "the caches dir in the Gradle user home is part of the global caches"() {
        def globalCachesLocation = executer.gradleUserHomeDir.file('caches').absolutePath
        buildFile << """
            assert services.get(${GlobalCacheLocations.name}).isInsideGlobalCache('${TextUtil.escapeString(globalCachesLocation)}')
        """

        expect:
        succeeds "help"
    }

    // This makes sure the next Gradle run starts with a clean BuildOutputCleanupRegistry
    private void invalidateBuildOutputCleanupState() {
        file(".gradle/buildOutputCleanup/cache.properties").text = """
            gradle.version=1.0
        """
    }

    private static String sourceFileWithGreeting(String greeting) {
        """
            public class Main {
                public static void main(String... args) {
                    System.out.println("$greeting");
                }
            }
        """
    }

    private static String taskWithGreeting(String greeting) {
        """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class PrinterTask extends DefaultTask {
                @TaskAction
                public void execute() {
                    System.out.println("$greeting");
                }
            }
        """
    }

    void assertWatchedRootDirectories(List<Set<File>> expectedWatchedRootDirectories) {
        if (OperatingSystem.current().linux) {
            // There is no info logging for non-hierarchical watchers
            return
        }
        assert determineWatchedBuildRootDirectories(output) == expectedWatchedRootDirectories
    }

    private static List<Set<File>> determineWatchedBuildRootDirectories(String output) {
        output.readLines()
            .findAll { it.contains("] as root project directories") }
            .collect { line ->
                def matcher = line =~ /Now considering watching \[(.*)\] as root project directories/
                String directories = matcher[0][1]
                return directories.split(', ').collect { new File(it) } as Set
            }
    }
}
