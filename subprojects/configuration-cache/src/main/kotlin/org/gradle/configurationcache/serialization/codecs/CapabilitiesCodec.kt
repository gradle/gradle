/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.capabilities.Capability
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.internal.component.external.model.ImmutableCapability


object CapabilitiesCodec : Codec<Capability> {

    override suspend fun WriteContext.encode(value: Capability) {
        writeString(value.group)
        writeString(value.name)
        writeString(value.version)
    }

    override suspend fun ReadContext.decode(): Capability {
        val group = readString()
        val name = readString()
        val version = readString()
        return ImmutableCapability(group, name, version)
    }
}
