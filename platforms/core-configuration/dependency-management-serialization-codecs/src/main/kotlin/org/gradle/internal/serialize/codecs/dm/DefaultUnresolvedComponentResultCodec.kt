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
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedComponentResult
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext

/**
 * [Codec] for [DefaultUnresolvedComponentResult] used for serializing unresolved components to the configuration cache.
 */
class DefaultUnresolvedComponentResultCodec : Codec<DefaultUnresolvedComponentResult> {
    private
    val componentIdSerializer = ComponentIdentifierSerializer()

    override suspend fun WriteContext.encode(value: DefaultUnresolvedComponentResult) {
        componentIdSerializer.write(this, value.id)
        write(value.failure)
    }

    override suspend fun ReadContext.decode(): DefaultUnresolvedComponentResult {
        val componentId = componentIdSerializer.read(this)
        val failure = read() as Throwable
        return DefaultUnresolvedComponentResult(componentId, failure)
    }
}
