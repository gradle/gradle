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
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifierInternal
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

internal fun serialize(componentId: ComponentIdentifier): ByteArray {
    val bytes = ByteArrayOutputStream()
    val encoder = KryoBackedEncoder(bytes)
    val serializer = ComponentIdentifierSerializer()
    serializer.write(encoder, componentId)
    encoder.flush()
    return bytes.toByteArray()
}

internal fun deserialize(bytes: ByteArray): ComponentIdentifier {
    val decoder = KryoBackedDecoder(bytes.inputStream())
    val serializer = ComponentIdentifierSerializer()
    return serializer.read(decoder)
}

internal fun ScriptComponentSourcesRequest.deserializeIdentifiers(): Map<File, List<ComponentIdentifier>> =
    sourceComponentIdentifiers
        .map { it as ScriptComponentSourceIdentifierInternal }
        .groupBy { it.scriptFile }
        .mapValues { entry ->
            entry.value.map { deserialize(it.componentIdentifierBytes) }
        }


internal fun DependencyHandler.downloadSources(identifiers: Map<File, List<ComponentIdentifier>>): Map<ScriptComponentSourceIdentifier, List<File>> {

    val sourcesArtifacts = createArtifactResolutionQuery()
        .forComponents(identifiers.values.flatten())
        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
        .execute()
        .resolvedComponents
        .map { it.id to it.getArtifacts(SourcesArtifact::class.java) }

    val results: Map<ComponentIdentifier, List<File>> = sourcesArtifacts
        .filter { it.second.any { it is ResolvedArtifactResult } }
        .associate { it.first to it.second.filterIsInstance<ResolvedArtifactResult>().map { it.file } }


    fun scriptFilesFor(componentIdentifier: ComponentIdentifier): List<File> {
        return identifiers.filter { it.value.contains(componentIdentifier) }.keys.toList()
    }

    val reconciled: Map<ScriptComponentSourceIdentifier, MutableList<File>> = buildMap {
        results.forEach { (identifier, artifacts) ->
            scriptFilesFor(identifier).forEach { scriptFile ->
                val key = StandardScriptComponentSourceIdentifier(identifier.displayName, scriptFile, serialize(identifier))
                if (!containsKey(key)) {
                    put(key, mutableListOf())
                }
                getValue(key).addAll(artifacts)
            }
        }
    }
    return reconciled
}
