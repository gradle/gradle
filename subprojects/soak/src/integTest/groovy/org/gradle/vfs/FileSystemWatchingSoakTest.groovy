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

package org.gradle.vfs

import org.gradle.integtests.fixtures.FileSystemWatchingFixture
import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files

class FileSystemWatchingSoakTest extends DaemonIntegrationSpec implements FileSystemWatchingFixture {

    private static final int NUMBER_OF_SUBPROJECTS = 50
    private static final int NUMBER_OF_SOURCES_PER_SUBPROJECT = 100
    private static final double LOST_EVENTS_RATIO_MAC_OS = 0.6
    private static final double LOST_EVENTS_RATIO_WINDOWS = 0.1

    List<TestFile> sourceFiles

    def setup() {
        def subprojects = (1..NUMBER_OF_SUBPROJECTS).collect { "project$it" }
        def rootProject = multiProjectBuild("javaProject", subprojects) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'

                    tasks.withType(JavaCompile).configureEach {
                        options.fork = true
                    }
                }
            """
        }
        sourceFiles = subprojects.collectMany { projectDir ->
            (1..NUMBER_OF_SOURCES_PER_SUBPROJECT).collect {
                def sourceFile = rootProject.file("${projectDir}/src/main/java/my/domain/Dummy${it}.java")
                modifySourceFile(sourceFile, 0)
                return sourceFile
            }
        }

        executer.beforeExecute {
            withWatchFs()
            // running in parallel, so the soak test doesn't take this long.
            withArgument("--parallel")
        }
    }

    def "file watching works with multiple builds on the same daemon"() {
        def numberOfChangesBetweenBuilds = maxFileChangesWithoutOverflow

        when:
        succeeds("assemble")
        // Assemble twice, so everything is up-to-date and nothing is invalidated
        succeeds("assemble")
        def daemon = daemons.daemon
        def retainedFilesInLastBuild = retainedFilesInCurrentBuild
        then:
        daemon.assertIdle()

        expect:
        long endOfDaemonLog = getLinesInDaemonLog(daemon)
        50.times { iteration ->
            // when:
            changeSourceFiles(iteration, numberOfChangesBetweenBuilds)
            waitForChangesToBePickedUp()
            succeeds("assemble")

            // then:
            assert daemons.daemon.logFile == daemon.logFile
            daemon.assertIdle()
            assertWatchingSucceeded()
            boolean overflowDetected = detectOverflow(daemon, endOfDaemonLog)
            if (!overflowDetected) {
                int expectedNumberOfRetainedFiles = retainedFilesInLastBuild - numberOfChangesBetweenBuilds
                int retainedFilesAtTheBeginningOfTheCurrentBuild = retainedFilesSinceLastBuild
                assert retainedFilesAtTheBeginningOfTheCurrentBuild <= expectedNumberOfRetainedFiles
                assert expectedNumberOfRetainedFiles - 100 <= retainedFilesAtTheBeginningOfTheCurrentBuild
            }
            assert receivedFileSystemEventsSinceLastBuild >= minimumExpectedFileSystemEvents(numberOfChangesBetweenBuilds, 1)
            retainedFilesInLastBuild = retainedFilesInCurrentBuild
            endOfDaemonLog = getLinesInDaemonLog(daemon)
        }
    }

    def "file watching works with many changes between two builds"() {
        // Use 40 minutes idle timeout since the test may be running longer with an idle daemon
        executer.withDaemonIdleTimeoutSecs(2400)
        def numberOfChangedSourcesFilesPerBatch = maxFileChangesWithoutOverflow
        def numberOfChangeBatches = 500

        when:
        succeeds("assemble")
        // Assemble twice, so everything is up-to-date and nothing is invalidated
        succeeds("assemble")
        def daemon = daemons.daemon
        then:
        daemon.assertIdle()

        when:
        numberOfChangeBatches.times { iteration ->
            changeSourceFiles(iteration, numberOfChangedSourcesFilesPerBatch)
            waitBetweenChangesToAvoidOverflow()
        }
        boolean overflowDetected = detectOverflow(daemon, 0)
        // This needs to happen here, since retainedFilesInCurrentBuild looks at the log of the last executed build.
        int expectedNumberOfRetainedFiles = retainedFilesInCurrentBuild - numberOfChangedSourcesFilesPerBatch
        then:
        succeeds("assemble")
        daemons.daemon.logFile == daemon.logFile
        daemon.assertIdle()
        assertWatchingSucceeded()
        receivedFileSystemEventsSinceLastBuild >= minimumExpectedFileSystemEvents(numberOfChangedSourcesFilesPerBatch, numberOfChangeBatches)
        if (!overflowDetected) {
            assert retainedFilesSinceLastBuild == expectedNumberOfRetainedFiles
        }
    }

    private static getMaxFileChangesWithoutOverflow() {
        OperatingSystem.current().windows
            ? 200
            : 1000
    }

    private static boolean detectOverflow(DaemonFixture daemon, long fromLine) {
        boolean overflowDetected = daemon.logContains(fromLine, FileWatcherRegistry.Type.INVALIDATED.toString())
        if (overflowDetected) {
            println "Detected overflow in watcher, no files will be retained for the next build"
        }
        overflowDetected
    }

    private static long getLinesInDaemonLog(DaemonFixture daemon) {
        Files.lines(daemon.logFile.toPath()).withCloseable { lines -> lines.count() }
    }

    private static void waitBetweenChangesToAvoidOverflow() {
            Thread.sleep(150)
    }

    private static int minimumExpectedFileSystemEvents(int numberOfChangedFiles, int numberOfChangesPerFile) {
        def currentOs = OperatingSystem.current()
        if (currentOs.macOsX) {
            // macOS coalesces the changes if the are in short succession
            return numberOfChangedFiles * numberOfChangesPerFile * LOST_EVENTS_RATIO_MAC_OS
        } else if (currentOs.linux) {
            // the JDK watchers only capture one event per watched path
            return numberOfChangedFiles
        } else if (currentOs.windows) {
            return numberOfChangedFiles * numberOfChangesPerFile * LOST_EVENTS_RATIO_WINDOWS
        }
        throw new AssertionError("Test not supported on OS ${currentOs}")
    }

    private void changeSourceFiles(int iteration, int number) {
        sourceFiles.take(number).each { sourceFile ->
            modifySourceFile(sourceFile, iteration + 1)
        }
    }

    private void assertWatchingSucceeded() {
        outputDoesNotContain("Couldn't create watch service")
        outputDoesNotContain("Couldn't fetch file changes, dropping VFS state")
        outputDoesNotContain("Dropped VFS state due to lost state")
    }

    private static void modifySourceFile(TestFile sourceFile, int numberOfMethods) {
        String className = sourceFile.name - ".java"
        sourceFile.text = """
            package my.domain;

            public class ${className} {
                ${ (1..numberOfMethods).collect { "public void doNothing${it}() {}" }.join("\n")}
            }
        """
    }
}
