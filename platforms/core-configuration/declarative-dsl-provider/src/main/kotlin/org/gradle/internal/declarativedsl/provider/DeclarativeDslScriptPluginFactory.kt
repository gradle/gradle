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

package org.gradle.internal.declarativedsl.provider

import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated
import org.gradle.internal.declarativedsl.interpreter.DeclarativeDslNotEvaluatedException
import org.gradle.internal.declarativedsl.interpreter.DeclarativeKotlinScriptEvaluator
import javax.inject.Inject


@Suppress("unused") // The name of this class is hardcoded in Gradle
internal
class DeclarativeDslScriptPluginFactory @Inject constructor(
    private val declarativeKotlinScriptEvaluator: DeclarativeKotlinScriptEvaluator,
    private val problems: InternalProblems
) : ScriptPluginFactory {

    override fun create(
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ): ScriptPlugin =
        DeclarativeDslPlugin(scriptSource) { target ->
            when (val result = declarativeKotlinScriptEvaluator.evaluate(target, scriptSource, targetScope)) {
                is EvaluationResult.Evaluated -> Unit
                is NotEvaluated -> {
                    reportEvaluationFailuresAsProblemsAndThrow(scriptSource, problems, result.stageFailures)
                }
            }

            targetScope.lock()
        }
}

private fun reportEvaluationFailuresAsProblemsAndThrow(
    scriptSource: ScriptSource,
    problems: InternalProblems,
    failures: List<NotEvaluated.StageFailure>
): Nothing {
    val failureProblems = failures.flatMap { stageFailure ->
        when (stageFailure) {
            is NotEvaluated.StageFailure.SchemaBuildingFailures ->
                schemaBuildingFailuresAsProblems(stageFailure, problems)

            else -> emptyList() // TODO: report all other DCL failures as problems
        }
    }
    problems.reporter.report(failureProblems)
    /**
     * Instead of [org.gradle.api.problems.ProblemReporter.throwing], we just throw
     * this single exception here to avoid duplicating the full message of the
     * exception on each of the problems.
     */
    throw DeclarativeDslNotEvaluatedException(scriptSource.fileName, failures)
}

