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

import groovy.lang.GroovyObjectSupport
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.beans.BeanFieldDeserializer
import org.gradle.instantexecution.serialization.beans.BeanFieldSerializer
import org.gradle.instantexecution.serialization.readClass
import org.gradle.instantexecution.serialization.writeClass
import sun.reflect.ReflectionFactory
import java.lang.reflect.Constructor


internal
class BeanCodec(
    private val filePropertyFactory: FilePropertyFactory
) : Codec<Any> {

    override fun WriteContext.encode(value: Any) {
        val id = isolate.identities.getId(value)
        if (id != null) {
            writeSmallInt(id)
        } else {
            writeSmallInt(isolate.identities.putInstance(value))
            val beanType = GeneratedSubclasses.unpackType(value)
            writeClass(beanType)
            BeanFieldSerializer(beanType).run {
                serialize(value)
            }
        }
    }

    override fun ReadContext.decode(): Any? {
        val id = readSmallInt()
        val previousValue = isolate.identities.getInstance(id)
        if (previousValue != null) {
            return previousValue
        }
        val beanType = readClass()
        val constructor = if (GroovyObjectSupport::class.java.isAssignableFrom(beanType)) {
            // Run the `GroovyObjectSupport` constructor, to initialize the metadata field
            newConstructorForSerialization(beanType, GroovyObjectSupport::class.java.getConstructor())
        } else {
            newConstructorForSerialization(beanType, Object::class.java.getConstructor())
        }
        val bean = constructor.newInstance()
        isolate.identities.putInstance(id, bean)
        BeanFieldDeserializer(bean.javaClass, filePropertyFactory).run {
            deserialize(bean)
        }
        return bean
    }

    // TODO: What about the runtime decorations a serialized bean might have had at configuration time?
    private
    fun newConstructorForSerialization(beanType: Class<*>, constructor: Constructor<*>): Constructor<out Any> =
        ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, constructor)
}
