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

package org.gradle.script.lang.kotlin.support

import org.gradle.api.Project
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.loggerFor

import org.jetbrains.kotlin.script.KotlinConfigurableScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptConfig
import org.jetbrains.kotlin.script.KotlinScriptParameterConfig

import java.io.File

object KotlinScriptDefinitionProvider {

    val LOGGER = loggerFor<KotlinScriptDefinitionProvider>()

    val GRADLE_API_NOTATION = DependencyFactory.ClassPathNotation.GRADLE_API.name

    val PROTO_SCRIPT_CONFIG = KotlinScriptConfig(
        name = "Kotlin build script",
        supertypes = arrayListOf(KotlinBuildScript::class.qualifiedName!!),
        parameters = arrayListOf(
            KotlinScriptParameterConfig("_project_hidden_", Project::class.qualifiedName!!)),
        superclassParamsMapping = arrayListOf("_project_hidden_"))

    fun selectGradleApiJars(classPathRegistry: ClassPathRegistry) =
        gradleApi(classPathRegistry).asFiles.filter { includeInClassPath(it.name) }

    fun gradleApi(classPathRegistry: ClassPathRegistry) = classPathRegistry.getClassPath(GRADLE_API_NOTATION)

    fun scriptDefinitionFor(classPath: List<File>): KotlinConfigurableScriptDefinition {
        LOGGER.info("Kotlin compilation classpath: {}", classPath)
        return KotlinConfigurableScriptDefinition(
            PROTO_SCRIPT_CONFIG.copy(classpath = classPath.asSequence().map { it.path }.toMutableList()),
            emptyMap())
    }

    private fun includeInClassPath(name: String): Boolean {
        return name.startsWith("kotlin-stdlib-")
            || name.startsWith("kotlin-reflect-")
            || name.startsWith("ant-")
            || name.startsWith("gradle-")
    }
}
