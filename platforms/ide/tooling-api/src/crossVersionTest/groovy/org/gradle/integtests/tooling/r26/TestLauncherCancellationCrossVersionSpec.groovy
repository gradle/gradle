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

package org.gradle.integtests.tooling.r26

import org.gradle.integtests.tooling.CancellationSpec
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection


class TestLauncherCancellationCrossVersionSpec extends CancellationSpec {
    def "can cancel test execution request during configuration phase"() {
        setupCancelInConfigurationBuild()

        def cancel = GradleConnector.newCancellationTokenSource()
        def sync = server.expectAndBlock("registered")
        def resultHandler = new TestResultHandler()

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newTestLauncher()
            build.withJvmTestClasses("Broken")
            build.withCancellationToken(cancel.token())
            build.run(resultHandler)
            sync.waitForAllPendingCalls(resultHandler)
            cancel.cancel()
            sync.releaseAll()
            resultHandler.finished()
        }

        then:
        buildWasCancelled(resultHandler, 'Could not execute tests using')
    }
}
