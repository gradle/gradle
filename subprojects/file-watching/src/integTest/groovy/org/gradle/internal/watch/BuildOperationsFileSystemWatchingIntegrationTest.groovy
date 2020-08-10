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

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.watch.vfs.impl.BuildFinishedFileSystemWatchingResult
import org.gradle.internal.watch.vfs.impl.BuildStartedFileSystemWatchingResult

class BuildOperationsFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)
    def projectDir = file("project")
    def inputFile = projectDir.file("input.txt")

    def setup() {
        projectDir.file("build.gradle") << """
            task myTask {
                outputs.file("build/output.txt")
                inputs.file("${inputFile.name}")
                doLast {
                    file("build/output.txt").text = file("${inputFile.name}").text
                }
            }
        """

        inputFile.text = "input"
        executer.beforeExecute {
            inDirectory(projectDir)
        }
    }

    def "emits build operations when watching is enabled"() {
        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped(":myTask")
        def startedResult = buildStartedResult()
        startedResult.watchingEnabled
        startedResult.startedWatching
        startedResult.statistics == null

        def finishedResult = buildFinishedResult()
        finishedResult.watchingEnabled
        !finishedResult.stoppedWatchingDuringTheBuild
        finishedResult.statistics
        retainedFiles(finishedResult)

        when:
        inputFile.text = "changed"
        waitForChangesToBePickedUp()
        withWatchFs().run ("myTask")
        startedResult = buildStartedResult()
        finishedResult = buildFinishedResult()
        then:
        startedResult.watchingEnabled
        !startedResult.startedWatching
        startedResult.statistics.fileWatchingStatistics.numberOfReceivedEvents > 0
        retainedFiles(startedResult)

        finishedResult.watchingEnabled
        !finishedResult.stoppedWatchingDuringTheBuild
        finishedResult.statistics
        retainedFiles(finishedResult)
    }

    def "emits build operations when watching is disabled"() {
        when:
        withoutWatchFs().run "myTask"
        then:
        executedAndNotSkipped(":myTask")
        def startedResult = buildStartedResult()
        watchingDisabled(startedResult)
        !startedResult.startedWatching

        def finishedResult = buildFinishedResult()
        watchingDisabled(finishedResult)
        !finishedResult.stoppedWatchingDuringTheBuild
    }

    private static void watchingDisabled(Map<String, ?> result) {
        !result.watchingEnabled
        result.statistics == null
    }

    private Map<String, ?> buildStartedResult() {
        operations.result(BuildStartedFileSystemWatchingResult.DISPLAY_NAME)
    }

    private Map<String, ?> buildFinishedResult() {
        operations.result(BuildFinishedFileSystemWatchingResult.DISPLAY_NAME)
    }

    private static boolean retainedFiles(Map<String, ?> result) {
        def statistics = result.statistics
        statistics.retainedRegularFiles > 0 ||
            statistics.retainedDirectories > 0 ||
            statistics.retainedMissingFiles > 0
    }
}
