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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SerializerCodec
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer


/**
 * Encodes Gradle's own [org.gradle.api.artifacts.component.ComponentArtifactIdentifier] implementations
 * with [ComponentArtifactIdentifierSerializer].
 *
 * The interface is public API, so other implementations may exist.
 * Those are not claimed by this codec, and are left to the codecs bound after it, such as the bean codec.
 */
object ComponentArtifactIdentifierCodec : EncodingProducer, Decoding {

    private
    val codec: Codec<Any> = SerializerCodec(ComponentArtifactIdentifierSerializer(ComponentIdentifierSerializer())).uncheckedCast()

    override fun encodingForType(type: Class<*>): Encoding? =
        codec.takeIf { ComponentArtifactIdentifierSerializer.isSupported(type) }

    override suspend fun ReadContext.decode(): Any? =
        codec.run { decode() }
}
