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

import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.kotlin.dsl.execution.EvalOption
import org.gradle.kotlin.dsl.execution.defaultEvalOptions

import java.util.*

import javax.inject.Inject


@Suppress("unused") // The name of this class is hardcoded in Gradle
class KotlinScriptPluginFactory @Inject internal constructor(
    private val kotlinScriptEvaluator: KotlinScriptEvaluator
) : ScriptPluginFactory {

    override fun create(
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ): ScriptPlugin =

        KotlinScriptPlugin(scriptSource) { target ->

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
}


private
val lenientModeScriptOptions = EnumSet.of(EvalOption.IgnoreErrors)
