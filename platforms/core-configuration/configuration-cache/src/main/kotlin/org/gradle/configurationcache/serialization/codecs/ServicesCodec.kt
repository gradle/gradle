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

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.internal.service.scopes.ServiceScope


class ServicesCodec : EncodingProducer, Decoding {
    override fun encodingForType(type: Class<*>): Encoding? {
        // TODO - perhaps query the isolate owner to see whether the value is in fact a service
        val serviceType = serviceType(GeneratedSubclasses.unpack(type))
        return if (serviceType != null) {
            OwnerServiceEncoding(serviceType)
        } else {
            null
        }
    }

    private
    fun serviceType(type: Class<*>): Class<*>? {
        if (type.getAnnotation(ServiceScope::class.java) != null) {
            return type
        }
        for (superInterface in type.interfaces) {
            val serviceType = serviceType(superInterface)
            if (serviceType != null) {
                return serviceType
            }
        }
        if (type.superclass != null) {
            return serviceType(type.superclass)
        }
        return null
    }

    override suspend fun ReadContext.decode(): Any? {
        return isolate.owner.service(readClass())
    }
}


internal
class OwnerServiceEncoding(val serviceType: Class<*>) : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        writeClass(serviceType)
    }
}
