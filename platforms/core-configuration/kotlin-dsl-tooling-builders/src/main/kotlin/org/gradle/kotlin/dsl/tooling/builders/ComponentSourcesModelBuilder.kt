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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.tooling.model.buildscript.ComponentSources
import org.gradle.tooling.model.buildscript.ComponentSourcesRequest
import org.gradle.tooling.model.buildscript.SourceComponentIdentifier
import org.gradle.tooling.model.buildscript.SourceComponentIdentifierInternal
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.File
import java.io.Serializable

object ComponentSourcesModelBuilder : ParameterizedToolingModelBuilder<ComponentSourcesRequest> {
    override fun canBuild(modelName: String): Boolean =
        ComponentSources::class.java.name.equals(modelName)

    override fun getParameterType(): Class<ComponentSourcesRequest> =
        ComponentSourcesRequest::class.java

    override fun buildAll(modelName: String, project: Project): ComponentSources =
        error("Should not be called")

    override fun buildAll(modelName: String, parameter: ComponentSourcesRequest, project: Project): ComponentSources {
        val componentIdentifiers = parameter.sourceComponentIdentifiers
            .map { it as SourceComponentIdentifierInternal }
            .map { deserialize(it.componentIdentifierBytes) }

        val sourcesArtifacts = project.buildscript.dependencies.createArtifactResolutionQuery()
            .forComponents(componentIdentifiers)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
            .execute()
            .resolvedComponents
            .map { it.id to it.getArtifacts(SourcesArtifact::class.java) }

        val results = sourcesArtifacts
            .filter { it.second.any { it is ResolvedArtifactResult } }
            .associate { it.first to it.second.filterIsInstance<ResolvedArtifactResult>().map { it.file } }

        return StandardComponentSources(
            buildMap {
                results.forEach { (compId, sourceFiles) ->
                    put(StandardSourceComponentIdentifier(compId.displayName, serialize(compId)), sourceFiles)
                }
            }
        )
    }
}


class StandardComponentSources(
    private val state: Map<SourceComponentIdentifier, List<File>>
) : ComponentSources, Serializable {
    override fun getSourcesByComponents(): Map<SourceComponentIdentifier, List<File>> =
        state
}
