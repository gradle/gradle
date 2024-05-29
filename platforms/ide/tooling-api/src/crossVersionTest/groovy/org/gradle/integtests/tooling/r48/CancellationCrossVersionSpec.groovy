/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.tooling.r48

import org.gradle.integtests.tooling.CancellationSpec
import org.gradle.integtests.tooling.fixture.ActionQueriesModelThatRequiresConfigurationPhase
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

@TargetGradleVersion(">=4.8")
class CancellationCrossVersionSpec extends CancellationSpec {
    def "can cancel phased build action execution during configuration phase"() {
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def action = connection.action()
            action.projectsLoaded(new ActionQueriesModelThatRequiresConfigurationPhase(), Stub(IntermediateResultHandlerCollector))
            def build = action.build()
            build.withCancellationToken(cancel.token())
            collectOutputs(build)
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        configureWasCancelled(resultHandler, "Could not run phased build action using")
    }
}
