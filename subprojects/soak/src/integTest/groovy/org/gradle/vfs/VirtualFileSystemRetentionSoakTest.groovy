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

import org.gradle.integtests.fixtures.VfsRetentionFixture
import org.gradle.integtests.fixtures.daemon.DaemonFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.vfs.watch.FileWatcherRegistry
import org.gradle.soak.categories.SoakTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.experimental.categories.Category

@Category(SoakTest)
// Fixme: See https://github.com/gradle/gradle/issues/12457
@Requires(TestPrecondition.MAC_OS_X)
class VirtualFileSystemRetentionSoakTest extends DaemonIntegrationSpec implements VfsRetentionFixture {

    private static final int NUMBER_OF_SUBPROJECTS = 50
    private static final int MAX_FILE_CHANGES_WITHOUT_OVERFLOW = 1000
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
            withRetention()
            // running in parallel, so the soak test doesn't take this long.
            withArgument("--parallel")
        }
    }

    def "file watching works with multiple builds on the same daemon"() {
        def numberOfChangesBetweenBuilds = MAX_FILE_CHANGES_WITHOUT_OVERFLOW

        when:
        succeeds("assemble")
        def daemon = daemons.daemon
        then:
        daemon.assertIdle()

        expect:
        boolean overflowDetected = false
        50.times { iteration ->
            // when:
            changeSourceFiles(iteration, numberOfChangesBetweenBuilds)
            waitForChangesToBePickedUp()
            succeeds("assemble")

            // then:
            assert daemons.daemon.logFile == daemon.logFile
            daemon.assertIdle()
            assertWatchingSucceeded()
            if (!overflowDetected) {
                overflowDetected = detectOverflow(daemon)
            }
            int expectedNumberOfRetainedFiles = retainedFilesInCurrentBuild - numberOfChangesBetweenBuilds
            if (overflowDetected) {
                assert retainedFilesSinceLastBuild in [expectedNumberOfRetainedFiles, 0]
            } else {
                assert retainedFilesSinceLastBuild == expectedNumberOfRetainedFiles
            }
            assert receivedFileSystemEventsSinceLastBuild >= minimumExpectedFileSystemEvents(numberOfChangesBetweenBuilds, 1)
        }
    }

    def "file watching works with many changes between two builds"() {
        // Use 40 minutes idle timeout since the test may be running longer with an idle daemon
        executer.withDaemonIdleTimeoutSecs(2400)
        def numberOfChangedSourcesFilesPerBatch = MAX_FILE_CHANGES_WITHOUT_OVERFLOW
        def numberOfChangeBatches = 500

        when:
        succeeds("assemble")
        def daemon = daemons.daemon
        then:
        daemon.assertIdle()

        when:
        numberOfChangeBatches.times { iteration ->
            changeSourceFiles(iteration, numberOfChangedSourcesFilesPerBatch)
            waitBetweenChangesToAvoidOverflow()
        }
        boolean overflowDetected = detectOverflow(daemon)
        int expectedNumberOfRetainedFiles = overflowDetected
            ? 0
            : retainedFilesInCurrentBuild - numberOfChangedSourcesFilesPerBatch
        then:
        succeeds("assemble")
        daemons.daemon.logFile == daemon.logFile
        daemon.assertIdle()
        assertWatchingSucceeded()
        receivedFileSystemEventsSinceLastBuild >= minimumExpectedFileSystemEvents(numberOfChangedSourcesFilesPerBatch, numberOfChangeBatches)
        retainedFilesSinceLastBuild == expectedNumberOfRetainedFiles
    }

    private static boolean detectOverflow(DaemonFixture daemon) {
        boolean overflowDetected = daemon.logContains(FileWatcherRegistry.Type.INVALIDATE.toString())
        if (overflowDetected) {
            println "Detected overflow in watcher, no files will be retained for the next build"
        }
        overflowDetected
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
