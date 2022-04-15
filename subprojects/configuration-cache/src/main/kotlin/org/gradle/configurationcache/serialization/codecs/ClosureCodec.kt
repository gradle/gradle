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

import groovy.lang.Closure
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext


object ClosureCodec : Codec<Closure<*>> {

    private
    val defaultOwner = Any()

    override suspend fun WriteContext.encode(value: Closure<*>) {
        // TODO - should write the owner, delegate and thisObject, replacing project and script references
        BeanCodec.run { encode(value.dehydrate()) }
    }

    override suspend fun ReadContext.decode(): Closure<*>? = BeanCodec.run {
        val closure = decode() as Closure<*>
        closure.rehydrate(null, defaultOwner, defaultOwner)
    }
}
