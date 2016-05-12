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

package org.gradle.script.lang.kotlin

import org.gradle.api.Project
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.jetbrains.kotlin.script.KotlinConfigurableScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptConfig
import org.jetbrains.kotlin.script.KotlinScriptParameterConfig
import org.jetbrains.kotlin.script.KotlinScriptSuperclassParameterConfig
import java.io.File

class KotlinScriptPluginFactory(val classPathRegistry: ClassPathRegistry) : ScriptPluginFactory {

    override fun create(scriptSource: ScriptSource, scriptHandler: ScriptHandler, targetScope: ClassLoaderScope,
                        baseScope: ClassLoaderScope, topLevelScript: Boolean): ScriptPlugin =
        KotlinScriptPlugin(scriptSource, compile(scriptSource, scriptHandler as ScriptHandlerInternal, targetScope))

    private fun compile(scriptSource: ScriptSource, scriptHandler: ScriptHandlerInternal,
                        targetScope: ClassLoaderScope): Class<*> {
        val scriptFile = scriptSource.resource.file
        val classPath = selectGradleApiJars()
        val scriptDef = scriptDefinitionFor(classPath)
        val classLoader = classLoaderFor(classPath, scriptHandler, targetScope)
        return compileKotlinScript(scriptFile, scriptDef, classLoader, LOGGER)
    }

    private fun selectGradleApiJars() = gradleApi().asFiles.filter { includeInClassPath(it.name) }

    private fun gradleApi() = classPathRegistry.getClassPath(GRADLE_API_NOTATION)

    private fun includeInClassPath(name: String): Boolean {
        return name.startsWith("kotlin-stdlib-")
            || name.startsWith("kotlin-reflect-")
            || name.startsWith("ant-")
            || name.startsWith("gradle-")
    }

    private fun classLoaderFor(classPath: List<File>, scriptHandler: ScriptHandlerInternal, targetScope: ClassLoaderScope): ClassLoader {
        scriptHandler.dependencies.add(ScriptHandler.CLASSPATH_CONFIGURATION, SimpleFileCollection(classPath))
        targetScope.export(scriptHandler.scriptClassPath)
        targetScope.export(KotlinBuildScript::class.java.classLoader)
        targetScope.lock()
        return targetScope.localClassLoader
    }

    private companion object {

        val LOGGER = loggerFor<KotlinScriptPluginFactory>()

        val GRADLE_API_NOTATION = DependencyFactory.ClassPathNotation.GRADLE_API.name

        val PROTO_SCRIPT_CONFIG = KotlinScriptConfig(
            name = "Kotlin build script",
            supertypes = arrayListOf(KotlinBuildScript::class.qualifiedName!!),
            parameters = arrayListOf(
                KotlinScriptParameterConfig("_project_hidden_", Project::class.qualifiedName!!)),
            superclassParamsMapping = arrayListOf(
                KotlinScriptSuperclassParameterConfig("_project_hidden_", Project::class.qualifiedName!!)))

        fun scriptDefinitionFor(classPath: List<File>): KotlinConfigurableScriptDefinition {
            LOGGER.info("Kotlin compilation classpath: {}", classPath)
            return KotlinConfigurableScriptDefinition(
                PROTO_SCRIPT_CONFIG.copy(classpath = classPath.asSequence().map { it.path }.toMutableList()),
                emptyMap())
        }
    }
}
