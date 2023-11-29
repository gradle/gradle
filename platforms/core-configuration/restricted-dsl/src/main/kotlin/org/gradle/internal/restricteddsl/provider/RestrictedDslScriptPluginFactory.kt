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

package org.gradle.internal.restricteddsl.provider

import org.gradle.api.GradleScriptException
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.FailuresInLanguageTree
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.FailuresInResolution
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoLanguageTree
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoParseResult
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.NoSchemaAvailable
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason.UnassignedValuesUsed
import javax.inject.Inject

/*
 * Copyright 2016 the original author or authors.
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


@Suppress("unused") // The name of this class is hardcoded in Gradle
class RestrictedDslScriptPluginFactory @Inject constructor(
    private val restrictedKotlinScriptEvaluator: RestrictedKotlinScriptEvaluator
) : ScriptPluginFactory {

    override fun create(
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ): ScriptPlugin =
        RestrictedDslPlugin(scriptSource) { target ->
            val result = restrictedKotlinScriptEvaluator.evaluate(target, scriptSource)
            when (result) {
                is RestrictedKotlinScriptEvaluator.EvaluationResult.Evaluated -> {
                    // We need to lock the scope here: we don't really need it now, but downstream scopes will rely on us locking it
                    // TODO: when the scope is used, this call should be removed
                    targetScope.lock()
                }
                is RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated -> reportErrors(scriptSource, result)
            }
        }

    private
    fun reportErrors(
        scriptSource: ScriptSource,
        evaluationResult: RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated
    ) {
        val exceptionMessage = buildString {
            append("Restricted DSL file '${scriptSource.fileName}': ")
            append(
                when (evaluationResult.reason) {
                    FailuresInLanguageTree -> "has failures in building the language tree"
                    FailuresInResolution -> "has failures in resolution"
                    NoLanguageTree -> "cannot be interpreted"
                    NoParseResult -> "failed to parse"
                    NoSchemaAvailable -> "has no associated schema"
                    UnassignedValuesUsed -> "used unassigned values"
                }
            )
        }
        throw GradleScriptException(exceptionMessage, RuntimeException())
    }
}
