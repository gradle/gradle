/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.initialization.BuildGateToken
import org.gradle.internal.filewatch.PendingChangesListener
import org.gradle.internal.filewatch.PendingChangesManager
import org.gradle.internal.filewatch.SingleFirePendingChangesListener
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class ContinuousBuildGateIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
        buildFile << """
            import ${BuildGateToken.canonicalName}
            import ${BuildGateToken.GateKeeper.canonicalName}

            class BuildGateKeeper implements Runnable {
                final BuildGateToken buildGate
                final BuildGateToken.GateKeeper gateKeeper
                BuildGateKeeper(BuildGateToken buildGate) {
                    this.buildGate = buildGate
                    this.gateKeeper = buildGate.createGateKeeper()
                }
                void run() {
                    boolean stop = false
                    while (!stop) {
                        def command = "${server.uri("command")}".toURL().text
                        if (command == "open") {
                            println("[GK] Open gate")
                            gateKeeper.open()
                        } else if (command == "close") {
                            println("[GK] Close gate")
                            gateKeeper.close()
                        } else if (command == "stop") {
                            println("[GK] stop")
                            stop = true
                        } else {
                            println("[GK] ? " + command)
                        }
                    }
                }
            }
            class BuildGateKeeperStarter {
                static BuildGateKeeper gateKeeper
                static void start(BuildGateToken buildGate) {
                    if (gateKeeper == null) {
                        println "Starting gatekeeper"
                        gateKeeper = new BuildGateKeeper(buildGate)
                        new Thread(gateKeeper).start()
                    }
                }
            }

            def buildGate = gradle.services.get(BuildGateToken)
            BuildGateKeeperStarter.start(buildGate)

            class SimpleTask extends DefaultTask {
                @InputFile
                File inputFile = project.file("input.txt")
                
                @OutputFile
                File outputFile = new File(project.buildDir, "output.txt")
                
                @TaskAction
                void generate() {
                    outputFile.text = inputFile.text
                } 
            }
            
            def pendingChangesManager = gradle.services.get(${PendingChangesManager.canonicalName})
            pendingChangesManager.addListener new ${SingleFirePendingChangesListener.canonicalName}({
                ${server.callFromBuild("pending")}
            } as ${PendingChangesListener.canonicalName})
            
            task work(type: SimpleTask)
        """

        file("input.txt").text = "start"
    }

    def "build only starts when gate is opened"() {
        server.expect(server.resource("command", "close"))
        def command = server.expectAndBlock(server.resource("command", "open"))

        def inputFile = file("input.txt")
        def outputFile = file("build/output.txt")

        when:
        succeeds("work")
        then:
        outputFile.text == "start"

        when:
        // Make some file system changes to inputFile
        inputFile.text = "changed"
        then:
        // Gradle has detected the changes to inputFile
        def pending = server.expectAndBlock("pending")
        pending.waitForAllPendingCalls()
        pending.releaseAll()

        when:
        // command the gate keeper to open the gate and shutdown
        command.releaseAll()
        server.expect(server.resource("command", "stop"))
        then:
        // waits for build to start and finish
        succeeds()
        // Change has been incorporated
        outputFile.text == "changed"
    }
}
