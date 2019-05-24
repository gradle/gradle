/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.slf4j.Logger


internal
val classCodec: Codec<Class<*>> = codec(
    { writeClass(it) },
    { readClass() }
)


internal
val listCodec: Codec<List<*>> = codec(
    { writeCollection(it) },
    { readList() }
)


internal
val setCodec: Codec<Set<*>> = codec(
    { writeCollection(it) },
    { readSet() }
)


internal
val mapCodec: Codec<Map<*, *>> = codec(
    { writeMap(it) },
    { readMap() }
)


internal
val loggerCodec: Codec<Logger> = codec(
    { writeLogger(it) },
    { readLogger() }
)


internal
val artifactCollectionCodec: Codec<ArtifactCollection> = codec(
    {},
    { EmptyArtifactCollection(ImmutableFileCollection.of()) }
)


internal
fun <T> singleton(value: T): Codec<T> = SingletonCodec(value)


internal
data class SingletonCodec<T>(
    private val singleton: T
) : Codec<T> {

    override fun WriteContext.encode(value: T) = Unit

    override fun ReadContext.decode(): T? = singleton
}


internal
fun <T> codec(
    encode: WriteContext.(T) -> Unit,
    decode: ReadContext.() -> T?
): Codec<T> = object : Codec<T> {
    override fun WriteContext.encode(value: T) = encode(value)
    override fun ReadContext.decode(): T? = decode()
}
