/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification

@TargetGradleVersion("current")
class TapiAgentInstrumentationCrossVersionSpec extends ToolingApiSpecification {
    private String buildOutput

    def setup() {
        // TODO(mlopatkin) Figure a way to have agent-based instrumentation in the embedded TAPI mode.
        toolingApi.requireDaemons()
    }

    def "agent is enabled in TAPI by default"() {
        withDumpAgentStatusTask()

        when:
        runDumpTaskWithTapi()

        then:
        agentWasApplied()
    }

    def "agent is applied if enabled in settings"() {
        given:
        withAgentEnabledInProperties()
        withDumpAgentStatusTask()

        when:
        runDumpTaskWithTapi()

        then:
        agentWasApplied()
    }

    def "agent is not applied if disabled in settings"() {
        given:
        withAgentDisabledInProperties()
        withDumpAgentStatusTask()

        when:
        runDumpTaskWithTapi()

        then:
        agentWasNotApplied()
    }

    private void runDumpTaskWithTapi() {
        withConnection {
            buildOutput = withBuild {
                it.forTasks("hello")
            }.stdout.toString()
        }
    }

    private void agentWasApplied() {
        agentStatusWas(true)
    }

    private void agentWasNotApplied() {
        agentStatusWas(false)
    }

    private void agentStatusWas(boolean applied) {
        assert buildOutput.contains("agent applied = $applied")
    }

    private void withAgentEnabledInProperties() {
        withAgentStatusInProperties(true)
    }

    private void withAgentDisabledInProperties() {
        withAgentStatusInProperties(false)
    }

    private void withAgentStatusInProperties(boolean shouldApply) {
        file("gradle.properties") << "org.gradle.internal.instrumentation.agent=$shouldApply"
    }

    private void withDumpAgentStatusTask() {
        buildFile << """
            import org.gradle.internal.agents.AgentStatus

            tasks.register('hello') {
                doLast {
                    def status = services.get(AgentStatus).isAgentInstrumentationEnabled()
                    println("agent applied = \${status}")
                }
            }

            defaultTasks 'hello'
        """
    }
}
