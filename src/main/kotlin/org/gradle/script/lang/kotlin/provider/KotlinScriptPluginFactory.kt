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

package org.gradle.script.lang.kotlin.provider

import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.scriptDefinitionFor
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.selectGradleApiJars

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.api.initialization.dsl.ScriptHandler

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal

import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.loggerFor

import java.io.File

class KotlinScriptPluginFactory(val classPathRegistry: ClassPathRegistry) : ScriptPluginFactory {

    val logger = loggerFor<KotlinScriptPluginFactory>()

    override fun create(scriptSource: ScriptSource, scriptHandler: ScriptHandler, targetScope: ClassLoaderScope,
                        baseScope: ClassLoaderScope, topLevelScript: Boolean): ScriptPlugin =
        KotlinScriptPlugin(scriptSource, compile(scriptSource, scriptHandler as ScriptHandlerInternal, targetScope))

    private fun compile(scriptSource: ScriptSource, scriptHandler: ScriptHandlerInternal,
                        targetScope: ClassLoaderScope): Class<*> {
        val scriptFile = scriptSource.resource.file
        val classPath = selectGradleApiJars(classPathRegistry)
        val scriptDef = scriptDefinitionFor(classPath)
        val classLoader = classLoaderFor(classPath, scriptHandler, targetScope)
        return compileKotlinScript(scriptFile, scriptDef, classLoader, logger)
    }

    private fun classLoaderFor(classPath: List<File>,
                               scriptHandler: ScriptHandlerInternal,
                               targetScope: ClassLoaderScope): ClassLoader {
        scriptHandler.addScriptClassPathDependency(SimpleFileCollection(classPath))
        targetScope.export(scriptHandler.scriptClassPath)
        targetScope.export(KotlinBuildScript::class.java.classLoader)
        targetScope.lock()
        return targetScope.localClassLoader
    }
}
