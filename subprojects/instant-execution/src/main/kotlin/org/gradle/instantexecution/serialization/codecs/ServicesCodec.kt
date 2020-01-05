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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.service.scopes.BuildTree


class ServicesCodec : EncodingProducer, Decoding {
    override fun encodingForType(type: Class<*>): Encoding? {
        // Only handle build tree scoped service for now
        // TODO - perhaps query the isolate owner to see whether the value is in fact a service
        return if (type.getAnnotation(BuildTree::class.java) != null) {
            OwnerServiceEncoding
        } else {
            null
        }
    }

    override suspend fun ReadContext.decode(): Any? {
        return isolate.owner.service(readClass())
    }
}


internal
object OwnerServiceEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        writeClass(value.javaClass)
    }
}
