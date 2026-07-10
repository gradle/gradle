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

import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.result.DefaultComponentArtifactsResult
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext

/**
 * [Codec] for [DefaultComponentArtifactsResult] used for serializing component artifacts results to the configuration cache.
 */
class DefaultComponentArtifactsResultCodec : Codec<DefaultComponentArtifactsResult> {
    private
    val componentIdSerializer = ComponentIdentifierSerializer()

    override suspend fun WriteContext.encode(value: DefaultComponentArtifactsResult) {
        componentIdSerializer.write(this, value.id)
        val artifacts = value.allArtifacts
        writeSmallInt(artifacts.size)
        for (artifact in artifacts) {
            write(artifact)
        }
    }

    override suspend fun ReadContext.decode(): DefaultComponentArtifactsResult {
        val componentId = componentIdSerializer.read(this)
        val result = DefaultComponentArtifactsResult(componentId)
        val artifactCount = readSmallInt()
        repeat((0 until artifactCount).count()) {
            val artifact = read() as ArtifactResult
            result.addArtifact(artifact)
        }
        return result
    }
}
