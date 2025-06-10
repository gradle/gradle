/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.promo

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.Logging
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * This class handles configuration cache promo message at the end of the build.
 *
 * Instances of this class are thread-safe.
 */
@ServiceScope(Scope.BuildTree::class)
class ConfigurationCachePromoHandler(
    private val documentationRegistry: DocumentationRegistry
) : RootBuildLifecycleListener, ProblemsListener {
    @Volatile
    private var hasProblems = false

    override fun afterStart() = Unit

    override fun beforeComplete() {
        if (hasProblems) {
            return
        }

        val docUrl = documentationRegistry.getDocumentationFor("configuration_cache", "config_cache:usage")
        // TODO(mlopatkin): finalize the message
        Logging.getLogger(ConfigurationCachePromoHandler::class.java).lifecycle("Consider enabling configuration cache to speed up this build: $docUrl")
    }

    override fun onProblem(problem: PropertyProblem) {
        hasProblems = true
    }

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
        hasProblems = true
    }

    override fun forIncompatibleTask(trace: PropertyTrace, reason: String): ProblemsListener = this

    override fun onExecutionTimeProblem(problem: PropertyProblem) = onProblem(problem)
}
