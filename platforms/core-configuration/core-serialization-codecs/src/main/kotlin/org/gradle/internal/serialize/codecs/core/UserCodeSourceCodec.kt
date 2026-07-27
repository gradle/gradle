/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.internal.Describables
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import java.net.URI


object UserCodeSourceCodec : Codec<UserCodeSource> {

    override suspend fun WriteContext.encode(value: UserCodeSource) {
        when (value) {
            is UserCodeSource.Binary -> {
                writeSmallInt(1)
                writeString(value.displayName.displayName)
                writeString(value.className)
                writeNullableString(value.pluginId)
            }
            is UserCodeSource.Script -> {
                writeSmallInt(2)
                writeString(value.displayName.displayName)
                writeNullableString(value.uri?.toString())
            }

            else -> error("Unexpected user code source type: ${value.javaClass.name}")
        }
    }

    override suspend fun ReadContext.decode(): UserCodeSource {
        return when (val type = readSmallInt()) {
            1 -> {
                val displayName = readString()
                val className = readString()
                val pluginId = readNullableString()
                UserCodeSource.Binary(Describables.of(displayName), className, pluginId)
            }

            2 -> {
                val displayName = readString()
                val uri = readNullableString()?.let { URI.create(it) }
                UserCodeSource.Script(Describables.of(displayName), uri)
            }

            else -> error("Unexpected user code source type: $type")
        }
    }

}
