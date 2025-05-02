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
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy.TransformMode


class ConfigurationCacheInjectedClasspathInstrumentationStrategy(
    private val problems: ProblemsListener
) : AbstractInjectedClasspathInstrumentationStrategy() {
    override fun whenThirdPartyAgentPresent(): TransformMode {
        // Report a problem and instrument anyway
        problems.onProblem(
            PropertyProblem(
                PropertyTrace.Gradle,
                StructuredMessage.build { text("support for using a Java agent with TestKit builds is not yet implemented with the configuration cache.") },
                documentationSection = DocumentationSection.NotYetImplementedTestKitJavaAgent
            )
        )
        return TransformMode.BUILD_LOGIC
    }
}
