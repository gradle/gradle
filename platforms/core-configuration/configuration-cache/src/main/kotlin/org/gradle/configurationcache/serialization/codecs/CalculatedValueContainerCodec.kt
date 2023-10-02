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

import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingSharedIdentity
import org.gradle.configurationcache.serialization.encodePreservingSharedIdentityOf
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.Describables
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator


class CalculatedValueContainerCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<CalculatedValueContainer<Any, ValueCalculator<Any>>> {
    override suspend fun WriteContext.encode(value: CalculatedValueContainer<Any, ValueCalculator<Any>>) {
        encodePreservingSharedIdentityOf(value) {
            val result: Any? = value.orNull
            if (result != null) {
                writeBoolean(true)
                write(result)
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
