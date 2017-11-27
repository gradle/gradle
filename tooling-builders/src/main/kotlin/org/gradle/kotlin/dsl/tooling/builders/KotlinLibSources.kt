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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult

import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier

import org.gradle.jvm.JvmLibrary

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.get

import org.gradle.language.base.artifact.SourcesArtifact


internal
fun sourcePathFor(scriptHandlers: List<ScriptHandler>): ClassPath {
    var sourcePath = ClassPath.EMPTY

    val resolvedDependencies = hashSetOf<ComponentIdentifier>()
    for (buildscript in scriptHandlers.asReversed()) {
        val unresolvedDependencies = classpathDependenciesOf(buildscript).filter { it !in resolvedDependencies }
        if (unresolvedDependencies.isNotEmpty()) {
            sourcePath += resolveSourcesUsing(buildscript.dependencies, unresolvedDependencies)
            resolvedDependencies += unresolvedDependencies
        }
    }

    if (!containsBuiltinKotlinModules(resolvedDependencies)) {
        sourcePath += kotlinLibSourcesFor(scriptHandlers)
    }

    return sourcePath
}


private
fun containsBuiltinKotlinModules(resolvedDependencies: HashSet<ComponentIdentifier>) =
    resolvedDependencies.containsAll(kotlinComponentIdentifiers)


private
fun classpathDependenciesOf(buildscript: ScriptHandler): List<ComponentIdentifier> =
    buildscript
        .configurations[CLASSPATH_CONFIGURATION]
        .incoming
        .artifactView { it.lenient(true) }
        .artifacts
        .map { it.id.componentIdentifier }


internal
fun kotlinLibSourcesFor(scriptHandlers: List<ScriptHandler>): ClassPath =
    scriptHandlers
        .asSequence()
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
val builtinKotlinModules = listOf("kotlin-stdlib-jre8", "kotlin-reflect")


private
val kotlinComponentIdentifiers by lazy {
    builtinKotlinModules.map(::kotlinComponent)
}


private
fun kotlinComponent(module: String): ComponentIdentifier =
    moduleId("org.jetbrains.kotlin", module, embeddedKotlinVersion)


private
fun moduleId(group: String, module: String, version: String): ModuleComponentIdentifier =
    DefaultModuleComponentIdentifier.newId(group, module, version)
