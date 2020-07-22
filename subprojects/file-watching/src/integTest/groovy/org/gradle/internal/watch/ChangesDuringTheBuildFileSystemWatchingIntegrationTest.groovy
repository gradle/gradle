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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class ChangesDuringTheBuildFileSystemWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

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
}
