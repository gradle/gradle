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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.scopeids.id.UserScopeId
import org.gradle.internal.scopeids.id.WorkspaceScopeId
import org.gradle.internal.time.Clock

class GradleEnterprisePluginBuildStateIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
    }

    def "provided build state is correct"() {
        given:
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            task check {
                doLast {
                    def service = serviceRef.get()
                    def buildState = service._buildState

                    assert buildState.buildStartedTime == services.get(${BuildStartedTime.name}).startTime

                    def clock = services.get(${Clock.name})
                    def timeDifference = Math.abs(buildState.currentTime - clock.currentTime)
                    assert timeDifference < 1000

                    assert buildState.buildInvocationId == services.get(${BuildInvocationScopeId.name}).id.asString()
                    assert buildState.workspaceId == services.get(${WorkspaceScopeId.name}).id.asString()
                    assert buildState.userId == services.get(${UserScopeId.name}).id.asString()

                    assert (buildState.daemonScanInfo != null) == ${GradleContextualExecuter.isDaemon()}
                }
            }
        """

        when:
        succeeds("check")

        then:
        executed(":check")

        when:
        succeeds("check")

        then:
        executed(":check")
    }

}
