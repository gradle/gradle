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

package org.gradle.configurationcache.serialization.codecs.transform

import org.gradle.api.internal.artifacts.transform.TransformationChain
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull


class TransformationChainCodec : Codec<TransformationChain> {
    override suspend fun WriteContext.encode(value: TransformationChain) {
        write(value.init)
        write(value.last)
    }

    override suspend fun ReadContext.decode(): TransformationChain {
        val init = read() as TransformationChain?
        val last = readNonNull<TransformationStep>()
        return TransformationChain(init, last)
    }
}
