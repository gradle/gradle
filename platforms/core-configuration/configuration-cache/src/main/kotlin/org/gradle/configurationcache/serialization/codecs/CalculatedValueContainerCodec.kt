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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.Describables
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator


class CalculatedValueContainerCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<CalculatedValueContainer<Any, ValueCalculator<Any>>> {
    override suspend fun WriteContext.encode(value: CalculatedValueContainer<Any, ValueCalculator<Any>>) {
        encodePreservingSharedIdentityOf(value) {
            if (value.isFinalized) {
                writeBoolean(true)
                write(value.get())
            } else {
                writeBoolean(false)
                write(value.supplier)
            }
        }
    }

    override suspend fun ReadContext.decode(): CalculatedValueContainer<Any, ValueCalculator<Any>>? {
        return decodePreservingSharedIdentity {
            val available = readBoolean()
            if (available) {
                val value = read()
                // TODO - restore the correct display name when the container is attached to its owner (rather than writing the display name to the cache)
                calculatedValueContainerFactory.create(Describables.of("unknown value"), value as Any)
            } else {
                val supplier = readNonNull<ValueCalculator<Any>>()
                calculatedValueContainerFactory.create(Describables.of("unknown value"), supplier)
            }
        }
    }
}
