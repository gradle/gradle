/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.jvm.JvmLibrary

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.get

import org.gradle.language.base.artifact.SourcesArtifact

import org.gradle.plugins.ide.internal.resolver.DefaultIdeDependencyResolver


internal
fun sourcePathFor(scriptHandlers: List<ScriptHandler>): ClassPath {
    var sourcePath = ClassPath.EMPTY
    val resolvedDependencies = hashSetOf<ModuleVersionIdentifier>()
    for (buildscript in scriptHandlers.asReversed()) {
        val classpathDependencies = classpathDependenciesOf(buildscript).filter { it !in resolvedDependencies }
        if (resolvedDependencies.addAll(classpathDependencies)) {
            sourcePath += resolveSourcesUsing(buildscript.dependencies, classpathDependencies.map { it.toModuleId() })
        }
    }

    if (!containsBuiltinKotlinModules(resolvedDependencies)) {
        sourcePath += kotlinLibSourcesFor(scriptHandlers)
    }

    return sourcePath
}


private
fun containsBuiltinKotlinModules(resolvedDependencies: HashSet<ModuleVersionIdentifier>) =
    resolvedDependencies.containsAll(
        builtinKotlinModules.map(::kotlinModuleVersionIdentifier))


private
fun classpathDependenciesOf(buildscript: ScriptHandler): List<ModuleVersionIdentifier> =
    DefaultIdeDependencyResolver()
        .getIdeRepoFileDependencies(buildscript.configurations[CLASSPATH_CONFIGURATION])
        .map { it.id }


private
fun ModuleVersionIdentifier.toModuleId() = moduleId(group, name, version)


internal
fun kotlinLibSourcesFor(scriptHandlers: List<ScriptHandler>): ClassPath =
    scriptHandlers
        .filter { it.repositories.isNotEmpty() }
        .map { resolveKotlinLibSourcesUsing(it.dependencies) }
        .find { !it.isEmpty } ?: ClassPath.EMPTY

private
fun resolveKotlinLibSourcesUsing(dependencyHandler: DependencyHandler): ClassPath =
    resolveSourcesUsing(dependencyHandler, kotlinComponentIdentifiers)

private
fun resolveSourcesUsing(dependencyHandler: DependencyHandler, components: List<ComponentIdentifier>): ClassPath =
    DefaultClassPath.of(
        dependencyHandler
            .createArtifactResolutionQuery()
            .forComponents(components)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
            .execute()
            .resolvedComponents
            .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
            .map { it.file })


private
val builtinKotlinModules = listOf("kotlin-stdlib", "kotlin-reflect")


private
val kotlinComponentIdentifiers by lazy {
    builtinKotlinModules.map(::kotlinComponent)
}


private
fun kotlinComponent(module: String): ComponentIdentifier =
    moduleId("org.jetbrains.kotlin", module, embeddedKotlinVersion)


private
fun kotlinModuleVersionIdentifier(module: String): ModuleVersionIdentifier =
    DefaultModuleVersionIdentifier("org.jetbrains.kotlin", module, embeddedKotlinVersion)


private
fun moduleId(group: String, module: String, version: String) =
    object : ModuleComponentIdentifier {
        override fun getGroup() = group
        override fun getModule() = module
        override fun getVersion() = version
        override fun getDisplayName() = "$group:$module:$version"
    }
