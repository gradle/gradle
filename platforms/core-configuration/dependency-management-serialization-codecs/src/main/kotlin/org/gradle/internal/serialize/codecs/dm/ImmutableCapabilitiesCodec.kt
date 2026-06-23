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

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext

class ImmutableCapabilitiesCodec : Codec<ImmutableCapabilities> {

    override suspend fun WriteContext.encode(value: ImmutableCapabilities) {
        val capabilities = value.asSet()
        writeSmallInt(capabilities.size)
        for (capability in capabilities) {
            writeString(capability.group)
            writeString(capability.name)
            writeNullableString(capability.version)
        }
    }

    override suspend fun ReadContext.decode(): ImmutableCapabilities {
        val size = readSmallInt()
        val capabilities = ImmutableSet.builderWithExpectedSize<ImmutableCapability>(size)
        repeat(size) {
            val group = readString()
            val name = readString()
            val version = readNullableString()
            capabilities.add(DefaultImmutableCapability(group, name, version))
        }
        return ImmutableCapabilities(capabilities.build())
    }

}
