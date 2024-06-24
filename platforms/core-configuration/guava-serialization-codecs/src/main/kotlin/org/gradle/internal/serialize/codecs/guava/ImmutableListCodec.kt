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

package org.gradle.internal.serialize.codecs.guava

import com.google.common.collect.ImmutableList
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection


object ImmutableListCodec : Codec<ImmutableList<Any>> {

    override suspend fun WriteContext.encode(value: ImmutableList<Any>) {
        writeCollection(value)
    }

    override suspend fun ReadContext.decode(): ImmutableList<Any>? {
        val size = readSmallInt()
        val builder = ImmutableList.builderWithExpectedSize<Any>(size)
        for (i in 0 until size) {
            val value = readNonNull<Any>()
            builder.add(value)
        }
        return builder.build()
    }
}
