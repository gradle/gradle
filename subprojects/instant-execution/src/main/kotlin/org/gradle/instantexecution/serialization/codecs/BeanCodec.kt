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

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.beans.SerializableWriteReplaceWriter
import org.gradle.instantexecution.serialization.withPropertyTrace
import java.lang.reflect.Method


internal
class BeanCodec : Codec<Any> {

    private
    val readResolveMethodCache = hashMapOf<Class<*>, Method?>()

    override suspend fun WriteContext.encode(value: Any) {
        val id = isolate.identities.getId(value)
        if (id != null) {
            writeSmallInt(id)
        } else {
            writeSmallInt(isolate.identities.putInstance(value))
            val beanType = GeneratedSubclasses.unpackType(value)
            withBeanTrace(beanType) {
                beanStateWriterFor(beanType).run {
                    if (this is SerializableWriteReplaceWriter) {
                        // When using the `writeReplace` strategy
                        // we don't want to serialize the beanType
                        // Class reference as it might not be directly
                        // resolvable as in the case of Java lambdas
                        writeClass(java.io.Serializable::class.java)
                    } else {
                        writeClass(beanType)
                    }
                    writeStateOf(value)
                }
            }
        }
    }

    override suspend fun ReadContext.decode(): Any? {
        val id = readSmallInt()
        val previousValue = isolate.identities.getInstance(id)
        if (previousValue != null) {
            return previousValue
        }
        val beanType = readClass()
        return withBeanTrace(beanType) {
            when (beanType) {
                java.io.Serializable::class.java -> {
                    val bean = readSerializableBean()!!
                    isolate.identities.putInstance(id, bean)
                    bean
                }
                else -> {
                    beanStateReaderFor(beanType).run {
                        val bean = newBean()
                        isolate.identities.putInstance(id, bean)
                        readStateOf(bean)
                        bean
                    }
                }
            }
        }
    }

    /**
     * Reads a bean resulting from a `writeReplace` method invocation
     * honouring `readResolve` if it's present.
     */
    private
    suspend fun ReadContext.readSerializableBean(): Any? {
        val bean = read()!!
        return when (val readResolve = readResolveMethodFor(bean)) {
            null -> bean
            else -> readResolve.invoke(bean)
        }
    }

    private
    fun readResolveMethodFor(bean: Any) =
        readResolveMethodCache.computeIfAbsent(bean.javaClass, ::findReadResolveMethod)

    private
    fun findReadResolveMethod(type: Class<*>): Method? = type
        .declaredMethods
        .firstOrNull { it.name == "readResolve" && it.parameters.isEmpty() }
        ?.apply { isAccessible = true }

    private
    inline fun <T : IsolateContext, R> T.withBeanTrace(beanType: Class<*>, action: () -> R): R =
        withPropertyTrace(PropertyTrace.Bean(beanType, trace)) {
            action()
        }
}
