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

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.loggerFor
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.BUILDSCRIPT_PROTOTYPE
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.scriptDefinitionFor
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.scriptDefinitionFromTemplate
import org.gradle.script.lang.kotlin.support.KotlinScriptDefinitionProvider.selectGradleApiJars

import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.classpath.DefaultClassPath

import java.io.File

import java.lang.reflect.InvocationTargetException

class KotlinScriptPluginFactory(val classPathRegistry: ClassPathRegistry) : ScriptPluginFactory {

    val logger = loggerFor<KotlinScriptPluginFactory>()

    override fun create(scriptSource: ScriptSource, scriptHandler: ScriptHandler,
                        targetScope: ClassLoaderScope, baseScope: ClassLoaderScope,
                        topLevelScript: Boolean): ScriptPlugin =
        KotlinScriptPlugin(scriptSource, compile(scriptSource, scriptHandler as ScriptHandlerInternal, targetScope))

    private fun compile(scriptSource: ScriptSource,
                        scriptHandler: ScriptHandlerInternal,
                        targetScope: ClassLoaderScope): (Project) -> Unit {
        val resource = scriptSource.resource
        val script = resource.text
        val scriptFile = resource.file
        val buildscriptRange = extractBuildScriptFrom(script)
        return when {
            buildscriptRange != null ->
                dualPhaseScript(scriptFile, script, buildscriptRange, scriptHandler, targetScope)
            else ->
                singlePhaseScript(scriptFile, targetScope)
        }
    }

    private fun singlePhaseScript(scriptFile: File, targetScope: ClassLoaderScope): (Project) -> Unit {
        val classPath = defaultClassPath()
        val scriptClass = compileScriptFile(scriptFile, classPath, classLoaderFor(classPath, targetScope))
        return { target -> executeScriptOf(scriptClass, target) }
    }

    private fun dualPhaseScript(scriptFile: File, script: String, buildscriptRange: IntRange,
                                scriptHandler: ScriptHandlerInternal,
                                targetScope: ClassLoaderScope): (Project) -> Unit {
        val defaultClassPath = defaultClassPath()
        val buildscriptClass = compileBuildscriptSection(buildscriptRange, script, defaultClassPath, targetScope)
        return { target ->
            executeScriptOf(buildscriptClass, target)
            val scriptClassLoader = scriptBodyClassLoaderFor(scriptHandler, targetScope)
            val effectiveClassPath = defaultClassPath + scriptHandler.scriptClassPath.asFiles
            val scriptClass = compileScriptFile(scriptFile, effectiveClassPath, scriptClassLoader)
            executeScriptOf(scriptClass, target)
        }
    }

    private fun classLoaderFor(classPath: List<File>, targetScope: ClassLoaderScope) =
        targetScope.run {
            export(DefaultClassPath.of(classPath))
            export(KotlinBuildScript::class.java.classLoader)
            lock()
            localClassLoader
        }

    private fun scriptBodyClassLoaderFor(scriptHandler: ScriptHandlerInternal, parentScope: ClassLoaderScope) =
        parentScope.createChild("${scriptHandler.sourceFile.name} body").run {
            export(scriptHandler.scriptClassPath)
            lock()
            localClassLoader
        }

    private fun compileBuildscriptSection(buildscriptRange: IntRange, script: String,
                                          classPath: List<File>, targetScope: ClassLoaderScope) =
        compileKotlinScript(
            tempBuildscriptFileFor(script.substring(buildscriptRange)),
            scriptDefinitionFor(classPath, prototype = BUILDSCRIPT_PROTOTYPE),
            classLoaderFor(classPath, targetScope),
            logger)

    private fun compileScriptFile(scriptFile: File, classPath: List<File>, classLoader: ClassLoader) =
        compileKotlinScript(scriptFile, scriptDefinitionFromTemplate(classPathRegistry), classLoader, logger)

    private fun executeScriptOf(scriptClass: Class<*>, target: Any) {
        try {
            scriptClass.getConstructor(Project::class.java).newInstance(target)
        } catch(e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun tempBuildscriptFileFor(buildscript: String) =
        createTempFile("buildscript", ".gradle.kts").apply {
            writeText(buildscript)
        }

    private fun defaultClassPath() =
        selectGradleApiJars(classPathRegistry)
}
