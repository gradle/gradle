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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.invocation.Gradle
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifierInternal
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import java.io.ByteArrayOutputStream
import java.io.File

sealed class SourceComponentIdentifierType(val discriminator: Int) {
    object GradleSrc : SourceComponentIdentifierType(0)
    data class GradleLib(val path: String) : SourceComponentIdentifierType(1)
    data class ExternalDependency(val id: ComponentIdentifier) : SourceComponentIdentifierType(2)
}

private class SourceComponentIdentifierSerializer : AbstractSerializer<SourceComponentIdentifierType>() {

    private val idSerializer = ComponentIdentifierSerializer()

    override fun write(encoder: Encoder, value: SourceComponentIdentifierType) {
        encoder.writeSmallInt(value.discriminator)
        when (value) {
            is SourceComponentIdentifierType.ExternalDependency -> idSerializer.write(encoder, value.id)
            is SourceComponentIdentifierType.GradleLib -> encoder.writeString(value.path)
            SourceComponentIdentifierType.GradleSrc -> Unit
        }
    }

    override fun read(decoder: Decoder): SourceComponentIdentifierType {
        return when (val discriminator = decoder.readSmallInt()) {
            0 -> SourceComponentIdentifierType.GradleSrc
            1 -> SourceComponentIdentifierType.GradleLib(decoder.readString())
            2 -> SourceComponentIdentifierType.ExternalDependency(idSerializer.read(decoder))
            else -> error("Unexpected discriminator: $discriminator")
        }
    }
}

internal fun newScriptComponentSourceIdentifier(
    displayName: String,
    scriptFile: File,
    identifier: SourceComponentIdentifierType
): ScriptComponentSourceIdentifier =
    StandardScriptComponentSourceIdentifier(
        displayName = displayName,
        scriptFile = scriptFile,
        bytes = serialize(identifier)
    )

private fun serialize(identifier: SourceComponentIdentifierType): ByteArray {
    val bytes = ByteArrayOutputStream()
    val encoder = KryoBackedEncoder(bytes)
    val serializer = SourceComponentIdentifierSerializer()
    serializer.write(encoder, identifier)
    encoder.flush()
    return bytes.toByteArray()
}

private fun deserialize(bytes: ByteArray): SourceComponentIdentifierType {
    val decoder = KryoBackedDecoder(bytes.inputStream())
    val serializer = SourceComponentIdentifierSerializer()
    return serializer.read(decoder)

}

internal fun ScriptComponentSourcesRequest.deserializeIdentifiers(): Map<File, List<SourceComponentIdentifierType>> =
    sourceComponentIdentifiers
        .map { it as ScriptComponentSourceIdentifierInternal }
        .groupBy { it.scriptFile }
        .mapValues { entry ->
            entry.value.map { deserialize(it.componentIdentifierBytes) }
        }


internal fun downloadSources(
    gradle: Gradle,
    dependencies: DependencyHandler,
    identifiers: Map<File, List<SourceComponentIdentifierType>>,
): Map<ScriptComponentSourceIdentifier, List<File>> {
    val reconciled: Map<ScriptComponentSourceIdentifier, MutableList<File>> = buildMap {

        // Gradle sources
        val gradleSource = SourceDistributionResolver(gradle)
        identifiers.forEach { (scriptFile, sourceIds) ->
            sourceIds.filterIsInstance<SourceComponentIdentifierType.GradleSrc>().forEach { gradleSrcIds ->
                put(
                    newScriptComponentSourceIdentifier(
                        displayName = "Gradle ${gradle.gradleVersion}",
                        scriptFile = scriptFile,
                        identifier = gradleSrcIds
                    ),
                    gradleSource.sourceDirs().toMutableList()
                )
            }
        }

        // External dependencies
        val externalIds = identifiers.values.flatten()
            .filterIsInstance<SourceComponentIdentifierType.ExternalDependency>().map { it.id }
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
                it.value.filterIsInstance<SourceComponentIdentifierType.ExternalDependency>()
                    .map { it.id }.contains(componentIdentifier)
            }.keys.toList()
        }
        results.forEach { (identifier, artifacts) ->
            scriptFilesFor(identifier).forEach { scriptFile ->
                val key = newScriptComponentSourceIdentifier(identifier.displayName, scriptFile, SourceComponentIdentifierType.ExternalDependency(identifier))
                if (!containsKey(key)) {
                    put(key, mutableListOf())
                }
                getValue(key).addAll(artifacts)
            }
        }

    }
    return reconciled
}
