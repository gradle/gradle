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

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.jvm.JvmLibrary

import org.gradle.language.base.artifact.SourcesArtifact

import org.gradle.kotlin.dsl.embeddedKotlinVersion

import kotlin.coroutines.experimental.buildSequence


internal
fun kotlinLibSourcesFor(project: Project): ClassPath =
    project
        .hierarchy
        .filter { it.buildscript.repositories.isNotEmpty() }
        .map { resolveKotlinLibSourcesUsing(it.buildscript.dependencies) }
        .find { !it.isEmpty } ?: ClassPath.EMPTY


private
fun resolveKotlinLibSourcesUsing(dependencyHandler: DependencyHandler): ClassPath =
    DefaultClassPath.of(
        dependencyHandler
            .createArtifactResolutionQuery()
            .forComponents(kotlinComponentIdentifiers)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
            .execute()
            .resolvedComponents
            .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
            .map { it.file })


private
val kotlinComponentIdentifiers by lazy {
    listOf(
        kotlinComponent("kotlin-stdlib"),
        kotlinComponent("kotlin-reflect"))
}


private
fun kotlinComponent(module: String): ComponentIdentifier =
    moduleId("org.jetbrains.kotlin", module, embeddedKotlinVersion)


private
fun moduleId(group: String, module: String, version: String) =
    object : ModuleComponentIdentifier
    {
        override fun getGroup() = group
        override fun getModule() = module
        override fun getVersion() = version
        override fun getDisplayName() = "$group:$module:$version"
    }


private
val org.gradle.api.Project.hierarchy: Sequence<Project>
    get() = buildSequence {
        var project = this@hierarchy
        yield(project)
        while (project != project.rootProject)
        {
            project = project.parent
            yield(project)
        }
    }
