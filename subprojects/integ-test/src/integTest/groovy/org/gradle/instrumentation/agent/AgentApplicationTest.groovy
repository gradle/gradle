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
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.agents.AgentStatus
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import spock.lang.Requires

// This test doesn't live in :instrumentation-agent to avoid the latter being implicitly added to
// the test runtime classpath as part of the main source set's output.
// It is important to have the agent appended to the classpath of all integration tests.
class AgentApplicationTest extends AbstractIntegrationSpec {
    def "agent is disabled by default"() {
        given:
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        agentWasNotApplied()
    }

    def "agent is not applied if disabled in the command-line"() {
        given:
        withoutAgent()
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        agentWasNotApplied()
    }

    @Requires(value = { GradleContextualExecuter.daemon }, reason = "Agent injection is not implemented for non-daemon and embedded modes")
    def "agent is applied to the daemon process running the build"() {
        given:
        withAgent()
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        agentWasApplied()
    }

    @Requires(value = { (GradleContextualExecuter.daemon && GradleContextualExecuter.configCache) }, reason = "Agent injection is not implemented for non-daemon and embedded modes")
    def "keeping agent status does not invalidate the configuration cache"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        given:
        withDumpAgentStatusAtConfiguration()

        when:
        withAgentApplied(agentStatus)
        succeeds()

        then:
        agentStatusWas(agentStatus)
        configurationCache.assertStateStored()

        when:
        withAgentApplied(agentStatus)
        succeeds()

        then:
        configurationCache.assertStateLoaded()

        where:
        agentStatus << [true, false]
    }

    @Requires(value = { (GradleContextualExecuter.daemon && GradleContextualExecuter.configCache) }, reason = "Agent injection is not implemented for non-daemon and embedded modes")
    def "changing agent status invalidates the configuration cache"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        given:
        withDumpAgentStatusAtConfiguration()

        when:
        withAgentApplied(useAgentOnFirstRun)
        succeeds()

        then:
        agentStatusWas(useAgentOnFirstRun)
        configurationCache.assertStateStored()

        when:
        withAgentApplied(useAgentOnSecondRun)
        succeeds()

        then:
        agentStatusWas(useAgentOnSecondRun)
        configurationCache.assertStateStored()

        where:
        useAgentOnFirstRun | useAgentOnSecondRun
        true               | false
        false              | true
    }

    @Requires(value = { GradleContextualExecuter.daemon }, reason = "Testing the daemons")
    def "daemon with the same agent status is reused"() {
        given:
        executer.requireIsolatedDaemons()
        withDumpAgentStatusTask()

        when:
        withAgentApplied(useAgentOnFirstRun)
        succeeds()

        then:
        daemons.daemon.becomesIdle()

        when:
        withAgentApplied(useAgentOnSecondRun)
        succeeds()

        then:
        def expectedDaemonCount = shouldReuseDaemon ? 1 : 2

        daemons.daemons.size() == expectedDaemonCount

        where:
        useAgentOnFirstRun | useAgentOnSecondRun || shouldReuseDaemon
        true               | true                || true
        false              | false               || true
        true               | false               || false
        false              | true                || false
    }

    @Requires(value = { GradleContextualExecuter.embedded }, reason = "Tests the embedded distribution")
    def "daemon spawned from embedded runner has agent enabled"() {
        given:
        executer.tap {
            // Force a separate daemon spawned by the InProcessGradleExecuter
            requireDaemon()
            requireIsolatedDaemons()
        }
        withAgent()
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        agentWasApplied()
    }

    private void withDumpAgentStatusTask() {
        buildFile("""
            import ${AgentStatus.name}

            tasks.register('hello') {
                doLast {
                    def status = services.get(AgentStatus).isAgentInstrumentationEnabled()
                    println("agent applied = \${status}")
                }
            }

            defaultTasks 'hello'
        """)
    }

    private void withDumpAgentStatusAtConfiguration() {
        buildFile("""
            import ${AgentStatus.name}

            def status = services.get(AgentStatus).isAgentInstrumentationEnabled()
            tasks.register('hello') {
                doLast {
                    println("agent applied = \$status")
                }
            }

            defaultTasks 'hello'
        """)
    }

    private void agentWasApplied() {
        agentStatusWas(true)
    }

    private void agentWasNotApplied() {
        agentStatusWas(false)
    }

    private void agentStatusWas(boolean applied) {
        outputContains("agent applied = $applied")
    }

    private void withAgent() {
        withAgentApplied(true)
    }

    private void withAgentApplied(boolean shouldApply) {
        executer.withArgument("-D${DaemonBuildOptions.ApplyInstrumentationAgentOption.GRADLE_PROPERTY}=$shouldApply")
    }

    private void withoutAgent() {
        withAgentApplied(false)
    }

    DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
