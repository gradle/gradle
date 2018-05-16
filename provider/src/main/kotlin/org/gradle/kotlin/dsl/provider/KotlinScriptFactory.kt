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


interface KotlinScriptFactory {

    fun kotlinScriptFor(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        baseScope: ClassLoaderScope,
        targetScope: ClassLoaderScope,
        topLevelScript: Boolean,
        inClassPathMode: Boolean
    ): KotlinScript
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

    override fun kotlinScriptFor(target: Any, scriptSource: ScriptSource, scriptHandler: ScriptHandler, baseScope: ClassLoaderScope, targetScope: ClassLoaderScope, topLevelScript: Boolean, inClassPathMode: Boolean): KotlinScript {
        val scriptTarget = kotlinScriptTargetFor(target, scriptSource, scriptHandler, baseScope, topLevelScript)
        val kotlinScriptSource = KotlinScriptSource(scriptSource)
        return compile(scriptTarget, kotlinScriptSource, scriptHandler, targetScope, baseScope, inClassPathMode)
    }

    private
    fun compile(
        scriptTarget: KotlinScriptTarget<Any>,
        scriptSource: KotlinScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        inClassPathMode: Boolean
    ): KotlinScript =

        compilerFor(scriptTarget, scriptSource, scriptHandler, targetScope, baseScope).run {

            if (inClassPathMode) compileForClassPath()
            else compile()
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
