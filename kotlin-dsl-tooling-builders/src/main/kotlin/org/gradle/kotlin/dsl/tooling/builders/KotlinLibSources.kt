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
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.result.ResolvedArtifactResult

import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

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
            sourcePath += resolveSourcesUsing(buildscript.dependencies) {
                forComponents(unresolvedDependencies)
            }
            resolvedDependencies += unresolvedDependencies
        }
    }

    if (!containsBuiltinKotlinModules(resolvedDependencies)) {
        sourcePath += kotlinLibSourcesFor(scriptHandlers)
    }

    return sourcePath
}


private
fun containsBuiltinKotlinModules(resolvedDependencies: Collection<ComponentIdentifier>): Boolean {
    val resolvedModules = resolvedDependencies.filterIsInstance<ModuleComponentIdentifier>()
    return builtinKotlinModules.all { kotlinModule ->
        resolvedModules.any { resolved ->
            resolved.module == kotlinModule && resolved.version == embeddedKotlinVersion
        }
    }
}


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
    resolveSourcesUsing(dependencyHandler) {
        builtinKotlinModules.forEach { kotlinModule ->
            forModule("org.jetbrains.kotlin", kotlinModule, embeddedKotlinVersion)
        }
    }


private
fun resolveSourcesUsing(dependencyHandler: DependencyHandler, query: ArtifactResolutionQuery.() -> Unit): ClassPath =
    DefaultClassPath.of(
        dependencyHandler
            .createArtifactResolutionQuery()
            .apply(query)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
            .execute()
            .resolvedComponents
            .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
            .map { it.file }
            .sorted()
    ) // TODO remove sorting once https://github.com/gradle/gradle/issues/5507 is fixed


private
val builtinKotlinModules = listOf("kotlin-stdlib-jdk8", "kotlin-reflect")
