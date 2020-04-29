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

package org.gradle.integtests.tooling.r65

import org.gradle.integtests.tooling.CancellationSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.util.concurrent.PollingConditions

@ToolingApiVersion(">=6.5")
class ToolingApiShutdownCrossVersionSpec extends CancellationSpec {

    def waitFor
    def existingDaemonPids

    def setup() {
        waitFor = new PollingConditions(timeout: 60, initialDelay: 0, factor: 1.25)
        existingDaemonPids = toolingApi.daemons.daemons.collect { it.context.pid }
    }

    @TargetGradleVersion(">=6.5")
    def "Disconnect during build stops daemon"() {
        setup:
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect() // using withConnection would call close after the closure

        def build = connection.newBuild()
        build.forTasks('hang')
        build.run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        resultHandler.finished()

        then:
        assertNoRunningDaemons()
    }

    @TargetGradleVersion(">=6.5")
    def "Disconnect during tooling model query stops daemon"() {
        setup:
        toolingApi.requireDaemons()
        buildFile << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    file {
                        whenMerged {
                            ${server.callFromBuild("waiting")}
                        }
                    }
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def query = connection.model(EclipseProject)
        query.get(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        resultHandler.finished()

        then:
        assertNoRunningDaemons()
    }

    @TargetGradleVersion(">=6.5")
    def "Disconnect stops multiple daemons"() {
        setup:
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection1 = connector.connect()
        ProjectConnection connection2 = connector.connect()

        def build = connection1.newBuild()
        build.forTasks('hang')
        build.run(resultHandler)

        def build2 = connection2.newBuild()
        build2.forTasks('hang')
        build2.run(resultHandler)

        then:
        assertNumberOfRunningDaemons(2)

        when:
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        resultHandler.finished()

        then:
        assertNoRunningDaemons()
    }

    def "Disconnect cancels the current build"() {
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def build = connection.newBuild()
        build.forTasks('hang')
        build.run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        resultHandler.finished()

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "Disconnect before build starts"() {
        when:
        GradleConnector connector = toolingApi.connector()
        connector.connect()
        connector.disconnect()

        then:
        notThrown(Exception)
        assertNoRunningDaemons()
    }

    def "Can call disconnect after the build was cancelled"() {
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def cancellation = GradleConnector.newCancellationTokenSource()
        def build = connection.newBuild()

        build.forTasks('hang').withCancellationToken(cancellation.token()).run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        cancellation.cancel()
        connector.disconnect()
        resultHandler.finished()

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "Can call cancel after disconnect"() {
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def cancellation = GradleConnector.newCancellationTokenSource()
        def build = connection.newBuild()

        build.forTasks('hang').withCancellationToken(cancellation.token()).run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        cancellation.cancel()
        resultHandler.finished()

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "Can call close after disconnect"() {
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def build = connection.newBuild()

        build.forTasks('hang').run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        connection.close()
        resultHandler.finished()

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "Can call disconnect after project connection closed"() {
        toolingApi.requireDaemons()
        buildFile << """
            task myTask {
                doLast {
                    println "myTask"
                }
            }
        """.stripIndent()


        when:
        GradleConnector connector = toolingApi.connector()
        withConnection(connector) { connection ->
            def build = connection.newBuild()
            build.forTasks('myTask').run()
        }
        connector.disconnect()

        then:
        true
    }

    def "Can call disconnect before project connection closed"() {
        toolingApi.requireDaemons()
        buildFile << """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        GradleConnector connector = toolingApi.connector()
        withConnection(connector) { connection ->
            def build = connection.newBuild()
            build.forTasks('hang').run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            connector.disconnect()
        }


        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "Cannot run build operations on project connection after disconnect"() {
        setup:
        GradleConnector connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()
        connection.getModel(GradleProject)
        connector.disconnect()

        when:
        connection.getModel(GradleProject)

        then:
        thrown(RuntimeException)
    }

    def "Cannot create new project connection after disconnect"() {
        setup:
        GradleConnector connector = toolingApi.connector()
        withConnection(connector) { connection ->
            connection.getModel(EclipseProject)
        }
        connector.disconnect()

        when:
        connector.connect()

        then:
        thrown(RuntimeException)
    }

    def assertNoRunningDaemons() {
        assertNumberOfRunningDaemons(0)
    }

    def assertNumberOfRunningDaemons(number) {
        waitFor.eventually {
            toolingApi.daemons.daemons.findAll { !existingDaemonPids.contains(it.context.pid) }.size() == number
        }
        true // `eventually` throws a runtime exception when the condition is never met
    }
}
