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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifierInternal
import org.gradle.tooling.model.buildscript.ScriptComponentSourcesRequest
import java.io.ByteArrayOutputStream
import java.io.File

internal sealed class ScriptComponentSourceIdentifierType(val discriminator: Int) {
    object GradleSrc : ScriptComponentSourceIdentifierType(0)
    data class GradleLib(val path: String) : ScriptComponentSourceIdentifierType(1)
    data class ExternalDependency(val id: ComponentIdentifier) : ScriptComponentSourceIdentifierType(2)
}

internal fun newScriptComponentSourceIdentifier(
    displayName: String,
    scriptFile: File,
    identifier: ScriptComponentSourceIdentifierType
): ScriptComponentSourceIdentifier =
    StandardScriptComponentSourceIdentifier(
        displayName = displayName,
        scriptFile = scriptFile,
        bytes = serialize(identifier)
    )

internal val ScriptComponentSourcesRequest.internalIdentifiers: List<ScriptComponentSourceIdentifierInternal>
    get() = sourceComponentIdentifiers.map { it as ScriptComponentSourceIdentifierInternal }

internal fun ScriptComponentSourcesRequest.deserializeIdentifiers(): Map<File, List<ScriptComponentSourceIdentifierType>> =
    sourceComponentIdentifiers
        .map { it as ScriptComponentSourceIdentifierInternal }
        .deserializeIdentifiers()

internal fun List<ScriptComponentSourceIdentifierInternal>.deserializeIdentifiers(): Map<File, List<ScriptComponentSourceIdentifierType>> =
    groupBy { it.scriptFile }
        .mapValues { entry ->
            entry.value.map { deserialize(it.scriptComponentSourceInternalBytes) }
        }

private object SourceComponentIdentifierSerializer : AbstractSerializer<ScriptComponentSourceIdentifierType>() {

    private val idSerializer = ComponentIdentifierSerializer()

    override fun write(encoder: Encoder, value: ScriptComponentSourceIdentifierType) {
        encoder.writeSmallInt(value.discriminator)
        when (value) {
            is ScriptComponentSourceIdentifierType.ExternalDependency -> idSerializer.write(encoder, value.id)
            is ScriptComponentSourceIdentifierType.GradleLib -> encoder.writeString(value.path)
            ScriptComponentSourceIdentifierType.GradleSrc -> Unit
        }
    }

    override fun read(decoder: Decoder): ScriptComponentSourceIdentifierType =
        when (val discriminator = decoder.readSmallInt()) {
            0 -> ScriptComponentSourceIdentifierType.GradleSrc
            1 -> ScriptComponentSourceIdentifierType.GradleLib(decoder.readString())
            2 -> ScriptComponentSourceIdentifierType.ExternalDependency(idSerializer.read(decoder))
            else -> error("Unexpected discriminator: $discriminator")
        }
}

private fun serialize(identifier: ScriptComponentSourceIdentifierType): ByteArray {
    val bytes = ByteArrayOutputStream()
    val encoder = KryoBackedEncoder(bytes)
    SourceComponentIdentifierSerializer.write(encoder, identifier)
    encoder.flush()
    return bytes.toByteArray()
}

private fun deserialize(bytes: ByteArray): ScriptComponentSourceIdentifierType =
    SourceComponentIdentifierSerializer.read(KryoBackedDecoder(bytes.inputStream()))
