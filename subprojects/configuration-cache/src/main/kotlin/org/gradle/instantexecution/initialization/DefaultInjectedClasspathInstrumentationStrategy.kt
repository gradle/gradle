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

package org.gradle.instantexecution.initialization

import org.gradle.instantexecution.problems.DocumentationSection
import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.problems.PropertyTrace
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import java.lang.management.ManagementFactory


class DefaultInjectedClasspathInstrumentationStrategy(private val startParameter: InstantExecutionStartParameter, private val problems: InstantExecutionProblems) : InjectedClasspathInstrumentationStrategy {
    override fun getTransform(): CachedClasspathTransformer.StandardTransform {
        val isAgentPresent = ManagementFactory.getRuntimeMXBean().inputArguments.find { it.startsWith("-javaagent:") } != null
        return if (!startParameter.isEnabled && isAgentPresent) {
            // Currently, the build logic instrumentation can interfere with Java agents, such as Jacoco
            // For now, disable the instrumentation
            CachedClasspathTransformer.StandardTransform.None
        } else if (isAgentPresent) {
            problems.onProblem(PropertyProblem(
                PropertyTrace.Gradle,
                StructuredMessage.build { text("support for using a Java agent with TestKit builds is not yet implemented with the configuration cache.") },
                null,
                DocumentationSection.NotYetImplementedTestKitJavaAgent
            ))
            CachedClasspathTransformer.StandardTransform.BuildLogic
        } else {
            CachedClasspathTransformer.StandardTransform.BuildLogic
        }
    }
}
