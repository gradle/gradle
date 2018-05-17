/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.initialization.ScriptHandlerInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider

import java.util.*


interface KotlinScriptFactory {

    fun kotlinScriptFor(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EnumSet<KotlinScriptOption>
    ): KotlinScript
}


enum class KotlinScriptOption {
    IgnoreErrors,
    SkipBody
}


typealias KotlinScript = () -> Unit


internal
class StandardKotlinScriptFactory(
    private val classPathProvider: KotlinScriptClassPathProvider,
    private val kotlinCompiler: CachingKotlinCompiler,
    private val classloadingCache: KotlinScriptClassloadingCache,
    private val pluginRequestsHandler: PluginRequestsHandler,
    private val embeddedKotlinProvider: EmbeddedKotlinProvider,
    private val classPathModeExceptionCollector: ClassPathModeExceptionCollector
) : KotlinScriptFactory {

    override fun kotlinScriptFor(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EnumSet<KotlinScriptOption>
    ): KotlinScript {

        val scriptTarget = kotlinScriptTargetFor(target, scriptSource, scriptHandler, baseScope, topLevelScript)
        val kotlinScriptSource = KotlinScriptSource(scriptSource)
        return compilerFor(scriptTarget, kotlinScriptSource, scriptHandler, targetScope, baseScope).run {
            if (KotlinScriptOption.IgnoreErrors in options)
                compileIgnoringErrors(executeScriptBody = KotlinScriptOption.SkipBody !in options)
            else
                compile()
        }
    }

    private
    fun compilerFor(
        scriptTarget: KotlinScriptTarget<Any>,
        scriptSource: KotlinScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope
    ) =

        KotlinScriptCompiler(
            kotlinCompiler,
            classloadingCache,
            scriptSource,
            scriptTarget,
            scriptHandler as ScriptHandlerInternal,
            pluginRequestsHandler,
            baseScope,
            targetScope,
            classPathProvider,
            embeddedKotlinProvider,
            classPathModeExceptionCollector)
}
