/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.dsl.tooling.builders

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.invocation.Gradle
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import java.io.File

@ServiceScope(Scope.Build::class)
internal class GradleScriptModelSources(
    private val gradle: Gradle
) {

    private val sourceDistroResolver = SourceDistributionResolver(gradle)

    fun downloadSources(
        identifiers: Map<File, List<ScriptComponentSourceIdentifierType>>,
        dependencies: DependencyHandler,
    ): Map<ScriptComponentSourceIdentifier, List<File>> {
        val reconciled: Map<ScriptComponentSourceIdentifier, MutableList<File>> = buildMap {

            // Gradle sources
            identifiers.forEach { (scriptFile, sourceIds) ->
                sourceIds.filterIsInstance<ScriptComponentSourceIdentifierType.GradleSrc>().forEach { gradleSrcIds ->
                    put(
                        newScriptComponentSourceIdentifier(
                            displayName = "Gradle API ${gradle.gradleVersion}",
                            scriptFile = scriptFile,
                            identifier = gradleSrcIds
                        ),
                        sourceDistroResolver.sourceDirs().toMutableList()
                    )
                }
            }

            // Distro libs and external dependencies
            val externalIds = identifiers.values.flatten()
                .filterIsInstance<ScriptComponentSourceIdentifierType.ExternalDependency>().map { it.id }
            val sourcesArtifacts = dependencies.createArtifactResolutionQuery()
                .forComponents(externalIds)
                .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
                .execute()
                .resolvedComponents
                .map { it.id to it.getArtifacts(SourcesArtifact::class.java) }
            val results: Map<ComponentIdentifier, List<File>> = sourcesArtifacts
                .filter { it.second.any { it is ResolvedArtifactResult } }
                .associate { it.first to it.second.filterIsInstance<ResolvedArtifactResult>().map { it.file } }

            fun scriptFilesFor(componentIdentifier: ComponentIdentifier): List<File> {
                return identifiers.filter {
                    it.value.filterIsInstance<ScriptComponentSourceIdentifierType.ExternalDependency>()
                        .map { it.id }.contains(componentIdentifier)
                }.keys.toList()
            }
            results.forEach { (identifier, artifacts) ->
                scriptFilesFor(identifier).forEach { scriptFile ->
                    val key = newScriptComponentSourceIdentifier(identifier.displayName, scriptFile, ScriptComponentSourceIdentifierType.ExternalDependency(identifier))
                    if (!containsKey(key)) {
                        put(key, mutableListOf())
                    }
                    getValue(key).addAll(artifacts)
                }
            }

        }
        return reconciled
    }
}
