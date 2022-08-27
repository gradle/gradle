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

import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.writeEnum
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.OrdinalNode


class OrdinalNodeCodec(
    private val ordinalGroups: OrdinalGroupFactory
) : Codec<OrdinalNode> {
    override suspend fun WriteContext.encode(value: OrdinalNode) {
        writeEnum(value.type)
        writeInt(value.ordinalGroup.ordinal)
    }

    override suspend fun ReadContext.decode(): OrdinalNode {
        val ordinalType = readEnum<OrdinalNode.Type>()
        val ordinal = readInt()
        return ordinalGroups.group(ordinal).locationsNode(ordinalType)
    }
}
