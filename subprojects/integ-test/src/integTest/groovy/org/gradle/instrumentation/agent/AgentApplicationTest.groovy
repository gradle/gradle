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
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.agents.AgentStatus
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

// This test doesn't live in :instrumentation-agent to avoid the latter being implicitly added to
// the test runtime classpath as part of the main source set's output.
// It is important to have the agent appended to the classpath of all integration tests.
class AgentApplicationTest extends AbstractIntegrationSpec {
    def "agent is enabled by default"() {
        given:
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        agentWasApplied()
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

    def "agent is applied to the daemon process running the build"() {
        given:
        withAgent()
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        agentWasApplied()
    }

    @Requires(
        value = IntegTestPreconditions.IsConfigCached,
        reason = "Tests the configuration cache behavior"
    )
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

    @Requires(
        value = IntegTestPreconditions.IsConfigCached,
        reason = "Tests the configuration cache behavior"
    )
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

    @Requires(
        value = IntegTestPreconditions.IsDaemonExecutor,
        reason = "Testing the daemons"
    )
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

    @Requires(
        value = IntegTestPreconditions.IsEmbeddedExecutor,
        reason = "Tests the embedded distribution"
    )
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

    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "Java 9 and above needs --add-opens to make environment variable mutation work"
    )
    def "foreground daemon respects the feature flag"() {
        given:
        executer.tap {
            requireDaemon()
            requireIsolatedDaemons()
        }

        def foregroundDaemon = startAForegroundDaemon(agentStatus)
        withAgentApplied(agentStatus)
        withDumpAgentStatusTask()

        when:
        succeeds()

        then:
        // Only one (the foreground one) daemon should be present
        daemons.getRegistry().getAll().size() == 1
        agentStatusWas(agentStatus)

        cleanup:
        foregroundDaemon?.abort()

        where:
        agentStatus << [true, false]
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

    DaemonLogsAnalyzer getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }

    GradleHandle startAForegroundDaemon(Boolean shouldApplyAgent = null) {
        int currentSize = daemons.getRegistry().getAll().size()
        if (shouldApplyAgent != null) {
            withAgentApplied(shouldApplyAgent)
        }
        def daemon = executer.noExtraLogging().withArgument("--foreground").start()
        // Wait for foreground daemon to be ready
        poll() { assert daemons.getRegistry().getAll().size() == (currentSize + 1) }
        return daemon
    }
}
