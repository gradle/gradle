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

package org.gradle.internal.cc.impl.initialization

import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.instrumentation.agent.AgentUtils
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy.TransformMode
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory


abstract class AbstractInjectedClasspathInstrumentationStrategy(
    private val problems: ProblemsListener
) : InjectedClasspathInstrumentationStrategy {
    override fun getTransform(): TransformMode {
        val isThirdPartyAgentPresent = ManagementFactory.getRuntimeMXBean().inputArguments.any { arg ->
            AgentUtils.isThirdPartyJavaAgentSwitch(arg) && !isAllowlistedAgent(arg)
        }
        return if (isThirdPartyAgentPresent) {
            reportThirdPartyAgentPresent()
            // Currently, the build logic instrumentation can interfere with Java agents, such as Jacoco
            // So, ignore, disable or fail or whatever based on which execution modes are enabled
            whenThirdPartyAgentPresent()
        } else {
            TransformMode.BUILD_LOGIC
        }
    }

    private fun isAllowlistedAgent(jvmArg: String): Boolean {
        val basename = agentBasenameOf(jvmArg) ?: return false
        val matched = effectiveAllowlist().any { it.equals(basename, ignoreCase = true) }
        if (matched) {
            logger.debug("Third-party Java agent '{}' is on the configuration-cache allowlist; skipping the CC problem.", basename)
        }
        return matched
    }

    private fun effectiveAllowlist(): Set<String> {
        val override = System.getProperty(ALLOWLIST_PROPERTY) ?: return BUILT_IN_ALLOWLIST
        return override.splitToSequence(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun reportThirdPartyAgentPresent() {
        problems.onProblem(
            PropertyProblem(
                PropertyTrace.Gradle,
                StructuredMessage.build {
                    text("support for using a Java agent with TestKit builds is not yet implemented with the configuration cache.")
                    text(" If you are confident the agent does not transform classes, list its file name in the ")
                    reference(ALLOWLIST_PROPERTY)
                    text(" system property (e.g. via ")
                    reference("org.gradle.jvmargs")
                    text(" in ")
                    reference("gradle.properties")
                    text(").")
                },
                documentationSection = DocumentationSection.NotYetImplementedTestKitJavaAgent
            )
        )
    }

    abstract fun whenThirdPartyAgentPresent(): TransformMode

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractInjectedClasspathInstrumentationStrategy::class.java)

        const val ALLOWLIST_PROPERTY = "org.gradle.internal.configuration-cache.java-agent-allowlist"

        /**
         * Known-inert (or sufficiently inert) Java agents that should not trigger
         * the configuration-cache problem for TestKit + agent. The list is
         * deliberately small and IntelliJ-flavoured; users can extend it via the
         * [ALLOWLIST_PROPERTY] system property (which replaces the built-ins).
         */
        val BUILT_IN_ALLOWLIST: Set<String> = setOf(
            "idea_rt.jar",
            "debugger-agent.jar",
            "captureAgent.jar"
        )

        private fun agentBasenameOf(jvmArg: String): String? {
            if (!AgentUtils.isJavaAgentSwitch(jvmArg)) return null
            val withoutPrefix = jvmArg.removePrefix("-javaagent:")
            val path = withoutPrefix.substringBefore('=')
            if (path.isEmpty()) return null
            return path.substringAfterLast('/').substringAfterLast('\\')
        }
    }
}
