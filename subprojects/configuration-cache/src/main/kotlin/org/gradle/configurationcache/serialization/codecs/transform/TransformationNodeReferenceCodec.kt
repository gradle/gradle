/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs.transform

import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext


/**
 * Serializes a reference to transformation nodes, asserting that the node has already been serialized earlier.
 *
 * This codec is used when serializing task state, which uses isolate and codecs that are different to
 * those used to serialize transformation nodes.
 */
internal
object TransformationNodeReferenceCodec : Codec<TransformationNode> {
    override suspend fun WriteContext.encode(value: TransformationNode) {
        val id = sharedIdentities.getId(value)
            ?: throw IllegalStateException("Node $value has not been encoded yet.")
        writeSmallInt(id)
    }

    override suspend fun ReadContext.decode(): TransformationNode {
        val id = readSmallInt()
        val instance = sharedIdentities.getInstance(id)
            ?: throw IllegalStateException("Node with id $id has not been decoded yet.")
        return instance as TransformationNode
    }
}
