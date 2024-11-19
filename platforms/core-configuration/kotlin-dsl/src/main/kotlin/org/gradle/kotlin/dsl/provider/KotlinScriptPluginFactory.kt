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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.logging.Logging
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.declarativedsl.evaluator.DeclarativeKotlinScriptEvaluator
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult
import org.gradle.kotlin.dsl.accessors.isDclEnabledForScriptTarget
import org.gradle.kotlin.dsl.execution.EvalOption
import org.gradle.kotlin.dsl.execution.defaultEvalOptions
import java.util.EnumSet
import javax.inject.Inject


@Suppress("unused") // The name of this class is hardcoded in Gradle
class KotlinScriptPluginFactory @Inject internal constructor(
    private val kotlinScriptEvaluator: KotlinScriptEvaluator,
    private val declarativeKotlinScriptEvaluator: DeclarativeKotlinScriptEvaluator
) : ScriptPluginFactory {

    override fun create(
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ): ScriptPlugin =
        KotlinScriptPlugin(scriptSource) { target ->
            if (shouldTryDclInterpreterWithScriptTarget(target) && isDclEnabledForScriptTarget(target)) {
                logger.info("Trying to interpret Kotlin DSL script ${scriptSource.fileName} with DCL")
                when (val result = declarativeKotlinScriptEvaluator.evaluate(target, scriptSource, targetScope)) {
                    is EvaluationResult.Evaluated -> {
                        logger.info("Successfully interpreted Kotlin DSL script ${scriptSource.fileName} with DCL")
                        targetScope.lock()
                        return@KotlinScriptPlugin
                    }
                    is EvaluationResult.NotEvaluated<*> -> logger.info("Failed to interpret Kotlin DSL script ${scriptSource.fileName} with DCL. Stage failures: ${result.stageFailures}$")
                }
            }

            kotlinScriptEvaluator
                .evaluate(
                    target,
                    scriptSource,
                    scriptHandler,
                    targetScope,
                    baseScope,
                    topLevelScript,
                    kotlinScriptOptions()
                )
        }

    private
    fun kotlinScriptOptions(): EnumSet<EvalOption> =
        if (inLenientMode()) lenientModeScriptOptions
        else defaultEvalOptions

    private val logger = Logging.getLogger(KotlinScriptPluginFactory::class.java)

    /**
     * Given the multi-stage nature of DCL settings files, we must handle the cases
     * when earlier stages like `pluginManagement` succeed but stages following them fail.
     * One example strategy is to make Kotlin DSL avoid re-running the successful DCL stages
     * and move right to the failed ones.
     *
     * That needs more changes in Kotlin DSL, and until we make them, we disable DCL interpretation
     * for any target other than [Project] (which is single-stage in DCL and is thus safe).
     */
    private fun shouldTryDclInterpreterWithScriptTarget(target: Any) =
        target is Project
}


private
val lenientModeScriptOptions = EnumSet.of(EvalOption.IgnoreErrors)
