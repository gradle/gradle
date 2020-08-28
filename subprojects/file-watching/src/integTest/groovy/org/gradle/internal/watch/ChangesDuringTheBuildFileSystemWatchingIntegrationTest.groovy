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

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class ChangesDuringTheBuildFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        executer.requireDaemon()
        server.start()
        executer.beforeExecute {
            withVerboseVfsLog()
        }
        buildFile << """
            import org.gradle.internal.file.FileType
            import org.gradle.internal.snapshot.*
            import org.gradle.internal.vfs.*

            task waitForUserChanges {
                doLast {
                    ${server.callFromBuild("userInput")}
                }
            }
        """
    }

    def "detects input file change just before the task is executed"() {
        def inputFile = file("input.txt")
        buildFile << """
            def inputFile = file("input.txt")
            def outputFile = file("build/output.txt")

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
        runWithFileSystemWatchingAndMakeChangesWhen("consumer", "userInput") {
            inputFile.text = "initial"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        // TODO: sometimes, the changes from the same build are picked up
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 0
        vfsLogs.retainedFilesSinceLastBuild == 0
        vfsLogs.retainedFilesInCurrentBuild >= 1

        when:
        runWithFileSystemWatchingAndMakeChangesWhen("consumer", "userInput") {
            inputFile.text = "changed"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        vfsLogs.receivedFileSystemEventsSinceLastBuild >= 0
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 1
        vfsLogs.retainedFilesSinceLastBuild >= 1
        vfsLogs.retainedFilesInCurrentBuild >= 2
    }

    def "detects input file change after the task has been executed"() {
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

            waitForUserChanges.dependsOn(consumer)
        """

        when:
        inputFile.text = "initial"
        runWithFileSystemWatchingAndMakeChangesWhen("waitForUserChanges", "userInput") {
            inputFile.text = "changed"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "initial"
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 0
        vfsLogs.retainedFilesSinceLastBuild == 0
        vfsLogs.retainedFilesInCurrentBuild > 0

        when:
        runWithFileSystemWatchingAndMakeChangesWhen("waitForUserChanges", "userInput") {
            inputFile.text = "changedAgain"
            waitForChangesToBePickedUp()
        }
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "changed"
        vfsLogs.receivedFileSystemEventsSinceLastBuild >= 0
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 1
        vfsLogs.retainedFilesSinceLastBuild > 0
        vfsLogs.retainedFilesInCurrentBuild > 0

        when:
        server.expect("userInput")
        withWatchFs().run("waitForUserChanges")
        then:
        executedAndNotSkipped(":consumer")
        outputFile.text == "changedAgain"
        vfsLogs.receivedFileSystemEventsSinceLastBuild >= 0
        vfsLogs.receivedFileSystemEventsInCurrentBuild >= 0
        vfsLogs.retainedFilesSinceLastBuild > 0
        vfsLogs.retainedFilesInCurrentBuild > 0
    }

    private void runWithFileSystemWatchingAndMakeChangesWhen(String task, String expectedCall, Closure action) {
        def handle = withWatchFs().executer.withTasks(task).start()
        def userInput = server.expectAndBlock(expectedCall)
        userInput.waitForAllPendingCalls()
        action()
        userInput.releaseAll()
        result = handle.waitForFinish()
    }
}
