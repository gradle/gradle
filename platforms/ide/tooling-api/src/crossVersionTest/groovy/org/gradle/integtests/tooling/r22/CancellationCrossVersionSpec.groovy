/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r22

import org.gradle.integtests.tooling.CancellationSpec
import org.gradle.integtests.tooling.fixture.ActionQueriesModelThatRequiresConfigurationPhase
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

class CancellationCrossVersionSpec extends CancellationSpec {

    def "can cancel build during settings phase"() {
        setupCancelInSettingsBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks(':sub:broken')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        buildWasCancelled(resultHandler)
    }

    def "can cancel build during configuration phase"() {
        file("gradle.properties") << "org.gradle.configureondemand=${configureOnDemand}"
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks(':sub:broken')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        buildWasCancelled(resultHandler)

        where:
        configureOnDemand << [true, false]
    }

    def "can cancel model creation during configuration phase"() {
        file("gradle.properties") << "org.gradle.configureondemand=${configureOnDemand}"
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject)
            model.withCancellationToken(cancel.token())
            model.get(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        configureWasCancelled(resultHandler, "Could not fetch model of type 'GradleProject' using")

        where:
        configureOnDemand << [true, false]
    }

    def "can cancel build action execution during settings phase"() {
        setupCancelInSettingsBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def action = connection.action(new ActionQueriesModelThatRequiresConfigurationPhase())
            action.withCancellationToken(cancel.token())
            action.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        configureWasCancelled(resultHandler, "Could not run build action using")
    }

    def "can cancel build action execution during configuration phase"() {
        file("gradle.properties") << "org.gradle.configureondemand=${configureOnDemand}"
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def action = connection.action(new ActionQueriesModelThatRequiresConfigurationPhase())
            action.withCancellationToken(cancel.token())
            action.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        configureWasCancelled(resultHandler, "Could not run build action using")

        where:
        configureOnDemand << [true, false]
    }

    def "can cancel build and skip some tasks"() {
        buildFile << """
task hang {
    doLast {
        ${waitForCancel()}
    }
}

task notExecuted(dependsOn: hang) {
    doLast {
        throw new RuntimeException("should not run")
    }
}
"""

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('notExecuted')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        taskWasCancelled(resultHandler, ":hang")
    }

    def "does not fail when scheduled tasks complete within the cancellation timeout"() {
        buildFile << """
task hang {
    doLast {
        ${waitForCancel()}
    }
}
"""

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        noExceptionThrown()
    }

    def "can cancel build through forced stop"() {
        // in-process call does not support forced stop
        toolingApi.requireDaemons()
        buildFile << """
task hang {
    doLast {
        ${server.callFromBuild("waiting")}
    }
}
"""
        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("waiting")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('hang')
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            resultHandler.finished()
        }

        then:
        resultHandler.assertFailedWith(BuildCancelledException)
        resultHandler.failure.message.startsWith("Could not execute build using")
        if (targetDist.toolingApiHasCauseOnForcedCancel) {
            resultHandler.failure.cause.message.startsWith("Daemon was stopped to handle build cancel request.")
        }
        // TODO - should have a failure report in the logging output
    }
}
