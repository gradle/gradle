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
import org.gradle.instantexecution.serialization.withPropertyTrace
import org.gradle.internal.reflect.ClassInspector
import java.io.Serializable
import java.lang.reflect.Method


internal
class BeanCodec : Codec<Any> {

    private
    val writeReplaceMethodCache = hashMapOf<Class<*>, Method?>()

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
                writeBeanOf(beanType, value)
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
            readBeanOf(beanType, id)
        }
    }

    private
    suspend fun WriteContext.writeBeanOf(beanType: Class<*>, value: Any) {
        // When the type is serializable and has a writeReplace() method,
        // then use this method to unpack the state of the object and serialize the result
        val writeReplace = beanType
            .takeIf { value is Serializable }
            ?.let { writeReplaceMethodFor(it) }
        when {
            writeReplace != null -> {
                // When using the `writeReplace` strategy we don't want to serialize
                // the beanType Class reference as it might not be directly resolvable
                // as in the case of Java lambdas so we use Serializable::class.java instead.
                writeClass(Serializable::class.java)
                write(writeReplace.invoke(value))
            }
            else -> beanStateWriterFor(beanType).run {
                writeClass(beanType)
                writeStateOf(value)
            }
        }
    }

    private
    suspend fun ReadContext.readBeanOf(beanType: Class<*>, id: Int): Any =
        when (beanType) {
            Serializable::class.java -> {
                val bean = readSerializableBean()!!
                isolate.identities.putInstance(id, bean)
                bean
            }
            else -> beanStateReaderFor(beanType).run {
                val bean = newBean()
                isolate.identities.putInstance(id, bean)
                readStateOf(bean)
                bean
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
    fun writeReplaceMethodFor(beanType: Class<*>) =
        writeReplaceMethodCache.computeIfAbsent(beanType, ::writeReplaceMethodOrNull)

    private
    fun readResolveMethodFor(beanReplacement: Any) =
        readResolveMethodCache.computeIfAbsent(beanReplacement.javaClass, ::readResolveMethodOrNull)

    private
    fun writeReplaceMethodOrNull(type: Class<*>): Method? =
        serializableMethodOrNull(type, "writeReplace")

    private
    fun readResolveMethodOrNull(type: Class<*>): Method? =
        serializableMethodOrNull(type, "readResolve")

    private
    fun serializableMethodOrNull(type: Class<*>, methodName: String): Method? =
        ClassInspector.inspect(type)
            .allMethods
            .find { it.name == methodName && it.parameters.isEmpty() }
            ?.apply { isAccessible = true }

    private
    inline fun <T : IsolateContext, R> T.withBeanTrace(beanType: Class<*>, action: () -> R): R =
        withPropertyTrace(PropertyTrace.Bean(beanType, trace)) {
            action()
        }
}
