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

package org.gradle.internal.serialize.codecs.dm

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.component.Artifact
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.internal.Describables
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.writeFile

/**
 * [Codec] for [DefaultResolvedArtifactResult] used for serializing resolved artifacts to the configuration cache.
 */
class DefaultResolvedArtifactResultCodec(
    private val attributesCodec: ImmutableAttributesCodec,
    private val capabilitiesCodec: ImmutableCapabilitiesCodec
) : Codec<DefaultResolvedArtifactResult> {

    override suspend fun WriteContext.encode(value: DefaultResolvedArtifactResult) {
        write(value.id)
        attributesCodec.run { encode(value.attributes) }
        capabilitiesCodec.run { encode(value.capabilities) }
        writeString(value.variant.displayName)
        writeClass(value.type)
        writeFile(value.file)
    }

    override suspend fun ReadContext.decode(): DefaultResolvedArtifactResult {
        val id = read() as ComponentArtifactIdentifier
        val attributes = attributesCodec.run { decode() }
        val capabilities = capabilitiesCodec.run { decode() }
        val displayName = readString()
        @Suppress("UNCHECKED_CAST") val type = readClass() as Class<out Artifact>
        val file = readFile()
        return DefaultResolvedArtifactResult(id, attributes, capabilities, Describables.of(displayName), type, file)
    }

}
