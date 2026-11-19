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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.service.scopes.ServiceScope


object ServicesCodec : SelectiveCodec {

    /**
     * Service types of the values passed to [encode], so only actual service classes get an entry.
     *
     * [accepts] doesn't use this cache, as it would add an entry for every type the codec rejects, which is
     * most of them. The blueprint already caches the result of [accepts] per type. The trade-off is that the
     * hierarchy of a service class is walked twice: in [accepts], and on the first [encode] of its value.
     */
    private
    val serviceTypes = object : ClassValue<Class<*>>() {
        override fun computeValue(type: Class<*>): Class<*> =
            requireNotNull(serviceTypeOf(type)) { "'${type.name}' is not a service type" }
    }

    override fun accepts(type: Class<*>): Boolean =
        serviceTypeOf(type) != null

    override suspend fun WriteContext.encode(value: Any) {
        writeClass(serviceTypes.get(value.javaClass))
    }

    private
    fun serviceTypeOf(type: Class<*>): Class<*>? =
        // TODO - perhaps query the isolate owner to see whether the value is in fact a service
        serviceType(GeneratedSubclasses.unpack(type))

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
