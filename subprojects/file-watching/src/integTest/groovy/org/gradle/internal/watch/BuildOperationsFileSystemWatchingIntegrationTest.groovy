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

class BuildOperationsFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits build operations when start watching"() {
        def projectDir = file("project")
        projectDir.file("build.gradle") << """
            task myTask {
                outputs.file("build/output.txt")
                inputs.file("input.txt")
                doLast {
                    file("build/output.txt").text = file("input.txt").text
                }
            }
        """

        def inputFile = projectDir.file("input.txt")
        inputFile.text = "input"
        executer.beforeExecute {
            inDirectory(projectDir)
        }


        when:
        withWatchFs().run "myTask"
        then:
        executedAndNotSkipped(":myTask")
        def startedResult = buildStartedResult()
        startedResult.startedWatching
        startedResult.fileWatchingStatistics == null
        !retainedFiles(startedResult)

        def finishedResult = buildFinishedResult()
        !finishedResult.stoppedWatchingDuringTheBuild
        finishedResult.fileWatchingStatistics
        retainedFiles(finishedResult)

        when:
        inputFile.text = "changed"
        waitForChangesToBePickedUp()
        withWatchFs().run ("myTask")
        startedResult = buildStartedResult()
        finishedResult = buildFinishedResult()
        then:
        !startedResult.startedWatching
        startedResult.fileWatchingStatistics.numberOfReceivedEvents > 0
        retainedFiles(startedResult)

        !finishedResult.stoppedWatchingDuringTheBuild
        retainedFiles(finishedResult)
        finishedResult.fileWatchingStatistics.numberOfReceivedEvents > 0
    }

    private Map<String, ?> buildFinishedResult() {
        operations.result("Build finished with file system watching")
    }

    private Map<String, ?> buildStartedResult() {
        operations.result("Build started with file system watching")
    }

    private static boolean retainedFiles(Map<String, ?> result) {
        result.retainedRegularFiles > 0 ||
            result.retainedDirectories > 0 ||
            result.retainedMissingFiles > 0
    }
}
