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
import org.gradle.test.fixtures.Flaky
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Retry
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

@Timeout(120)
@Retry(count = 3)
class ToolingApiShutdownCrossVersionSpec extends CancellationSpec {

    def waitFor

    def setup() {
        waitFor = new PollingConditions(timeout: 60, initialDelay: 0, factor: 1.25)
        toolingApi.requireIsolatedDaemons()
    }

    @TargetGradleVersion(">=6.5")
    def "disconnect during build stops daemon"() {
        setup:
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
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

    @Timeout(30)
    @TargetGradleVersion(">=6.8")
    def "disconnect during hanging build does not block the client"() {
        setup:
        file("gradle.properties") << "systemProp.org.gradle.internal.testing.daemon.hang=60000"
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
        ProjectConnection connection = connector.connect() // using withConnection would call close after the closure

        def build = connection.newBuild()
        build.forTasks('hang')
        build.run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        resultHandler.finished()
        then:
        noExceptionThrown()
    }

    @TargetGradleVersion(">=6.5")
    def "disconnect during tooling model query stops daemon"() {
        setup:
        buildFile.text = """
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
        def connector = toolingApi.connector()
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
    @Flaky
    def "disconnect stops multiple daemons"() {
        setup:
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectConcurrentAndBlock("waiting", "waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
        ProjectConnection connection1 = connector.connect()
        ProjectConnection connection2 = connector.connect()

        def build = connection1.newBuild()
        build.forTasks('hang')
        build.run(resultHandler)

        def build2 = connection2.newBuild()
        build2.forTasks('hang')
        build2.run(resultHandler)

        sync.waitForAllPendingCalls(resultHandler)
        then:
        assertNumberOfRunningDaemons(2)

        when:
        connector.disconnect()
        resultHandler.finished()
        then:
        assertNoRunningDaemons()
    }

    def "disconnect cancels the current build"() {
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
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

    def "disconnect before build starts"() {
        when:
        def connector = toolingApi.connector()
        connector.connect()
        connector.disconnect()

        then:
        notThrown(Exception)
        assertNoRunningDaemons()
    }

    def "can call disconnect after the build was cancelled"() {
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def cancellation = GradleConnector.newCancellationTokenSource()
        def build = connection.newBuild()

        build.forTasks('hang').withCancellationToken(cancellation.token()).run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        cancellation.cancel()
        Thread.sleep(200) // The daemon seems to drop messages arriving the same time
        connector.disconnect()
        resultHandler.finished()

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "can call cancel after disconnect"() {
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()

        def cancellation = GradleConnector.newCancellationTokenSource()
        def build = connection.newBuild()

        build.forTasks('hang').withCancellationToken(cancellation.token()).run(resultHandler)
        sync.waitForAllPendingCalls(resultHandler)
        connector.disconnect()
        Thread.sleep(200) // The daemon seems to drop messages arriving the same time
        cancellation.cancel()
        resultHandler.finished()

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "can call close after disconnect"() {
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
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

    def "can call disconnect after project connection closed"() {
        buildFile.text = """
            task myTask {
                doLast {
                    println "myTask"
                }
            }
        """.stripIndent()


        when:
        def connector = toolingApi.connector()
        withConnection(connector) { connection ->
            def build = connection.newBuild()
            build.forTasks('myTask').run()
        }
        connector.disconnect()
        then:
        noExceptionThrown()
    }

    def "can call disconnect before project connection closed"() {
        buildFile.text = """
            task hang {
                doLast {
                    ${server.callFromBuild("waiting")}
                }
            }
        """.stripIndent()

        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        def connector = toolingApi.connector()
        withConnection(connector) { connection ->
            def build = connection.newBuild()
            build.forTasks('hang').run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            connector.disconnect()
        }


        then:
        resultHandler.assertFailedWith(BuildCancelledException)
    }

    def "cannot run build operations on project connection after disconnect"() {
        setup:
        def connector = toolingApi.connector()
        ProjectConnection connection = connector.connect()
        connection.getModel(GradleProject)
        connector.disconnect()

        when:
        connection.getModel(GradleProject)

        then:
        def e = thrown(RuntimeException)
        e.message ==~ /Cannot use .* as it has been stopped./
    }

    def "cannot create new project connection after disconnect"() {
        setup:
        def connector = toolingApi.connector()
        withConnection(connector) { connection ->
            connection.getModel(EclipseProject)
        }
        connector.disconnect()

        when:
        connector.connect()

        then:
        def e = thrown(RuntimeException)
        e.message == "Tooling API client has been disconnected. No other connections may be used."
    }

    void assertNoRunningDaemons() {
        waitFor.eventually {
            toolingApi.daemons.daemons*.assertStopped()
        }
    }

    void assertNumberOfRunningDaemons(number) {
        waitFor.eventually {
            assert toolingApi.daemons.daemons.size() == number
            toolingApi.daemons.daemons*.assertBusy()
        }
        // `eventually` throws a runtime exception when the condition is never met
    }
}
