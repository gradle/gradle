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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.codec
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.readMap
import org.gradle.instantexecution.serialization.readSet
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.serialization.writeMap


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
