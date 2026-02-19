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

import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifierInternal
import org.gradle.tooling.model.buildscript.ScriptComponentSources
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder

object ScriptComponentSourcesModelBuilder : ParameterizedToolingModelBuilder<ScriptComponentSourcesRequest> {
    override fun canBuild(modelName: String): Boolean =
        ScriptComponentSources::class.java.name.equals(modelName)

    override fun getParameterType(): Class<ScriptComponentSourcesRequest> =
        ScriptComponentSourcesRequest::class.java

    override fun buildAll(modelName: String, project: Project): ScriptComponentSources =
        error("Should not be called")

    override fun buildAll(modelName: String, parameter: ScriptComponentSourcesRequest, project: Project): ScriptComponentSources {
        val componentIdentifiers = parameter.sourceComponentIdentifiers
            .map { it as ScriptComponentSourceIdentifierInternal }
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

        return StandardScriptComponentSources(
            buildMap {
                results.forEach { (compId, sourceFiles) ->
                    put(StandardScriptComponentSourceIdentifier(compId.displayName, serialize(compId)), sourceFiles)
                }
            }
        )
    }
}
