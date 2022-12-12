/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.instrumentation.agent

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.agents.AgentStatus
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Requires

class AgentApplicationTest extends AbstractIntegrationSpec {

    @Requires(value = { GradleContextualExecuter.daemon }, reason = "Agent injection is not implemented for non-daemon and embedded modes")
    def "agent is applied to the daemon process running the build"() {
        given:
        buildFile("""
            import ${AgentStatus.name}

            tasks.register('hello') {
                doLast {
                    println("agent applied = \${AgentStatus.isAgentApplied("org.gradle.instrumentation.agent.Agent")}")
                }
            }
        """)

        when:
        succeeds("hello")

        then:
        outputContains("agent applied = true")
    }

    @Requires(value = { !GradleContextualExecuter.daemon })
    @ToBeImplemented("Agent should be used in all modes eventually")
    def "agent is not applied in the unsupported modes yet"() {
        given:
        buildFile("""
            import ${AgentStatus.name}

            tasks.register('hello') {
                doLast {
                    println("agent applied = \${AgentStatus.isAgentApplied("org.gradle.instrumentation.agent.Agent")}")
                }
            }
        """)

        when:
        succeeds("hello")

        then:
        outputContains("agent applied = false")
    }
}
