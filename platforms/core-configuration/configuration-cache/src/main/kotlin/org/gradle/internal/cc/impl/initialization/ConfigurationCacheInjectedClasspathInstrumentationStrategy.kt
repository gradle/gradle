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
import org.gradle.internal.instrumentation.agent.AgentStatus
import org.gradle.internal.instrumentation.agent.ThirdPartyAgentDetection.isThirdPartyAgentPresent
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy.TransformMode

internal class ConfigurationCacheInjectedClasspathInstrumentationStrategy internal constructor(
    private val problems: ProblemsListener,
    private val agentStatus: AgentStatus,
) : InjectedClasspathInstrumentationStrategy {

    override fun getTransform(): TransformMode {
        if (!agentStatus.isAgentInstrumentationEnabled() && isThirdPartyAgentPresent()) {
            // Without Gradle's instrumentation agent, the buildscript classpath is rewritten ahead of time;
            // the third-party transformer would observe Gradle's bytecode, not the user's. Refuse the build
            // rather than produce wrong output like miscredited coverage.
            reportThirdPartyAgentPresent()
        }
        return TransformMode.BUILD_LOGIC
    }

    private fun reportThirdPartyAgentPresent() =
        problems.onProblem(
            PropertyProblem(
                PropertyTrace.Gradle,
                StructuredMessage.build { text("third-party Java agents with inactive Gradle's instrumentation agent are not supported by the configuration cache.") },
                documentationSection = DocumentationSection.RequirementsJavaAgent
            )
        )
}
