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

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.StringEncoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


/**
 * Deduplicates and encodes strings to a separate stream in a thread-safe manner.
 */
internal
class ParallelStringEncoder(
    stream: OutputStream,
    private val classEncoder: ClassEncoder
) : StringEncoder, ClassEncoder, AutoCloseable {

    private
    val strings = ConcurrentHashMap<Any, Int>()

    private
    var nextId = AtomicInteger(1)

    private
    val output = KryoBackedEncoder(stream)

    override fun writeNullableString(encoder: Encoder, string: CharSequence?) {
        if (string == null) {
            encoder.writeSmallInt(0)
        } else {
            writeString(encoder, string)
        }
    }

    override fun writeString(encoder: Encoder, string: CharSequence) {
        writeValue(encoder, string.toString(), ::doWriteString)
    }

    override fun close() {
        output.writeSmallInt(0) // EOF
        output.close()
    }

    override fun Encoder.encodeClass(type: Class<*>) {
        writeValue(this, type, ::doWriteClass)
    }

    override fun Encoder.encodeClassLoader(classLoader: ClassLoader?) {
        if (classLoader == null) {
            writeSmallInt(0)
        } else {
            writeValue(this, classLoader, ::doWriteClassLoader)
        }
    }

    private
    fun <T : Any> writeValue(encoder: Encoder, value: T, write: (T) -> Unit) {
        val id = strings.computeIfAbsent(value) { key ->
            val id = nextId.getAndIncrement()
            synchronized(output) {
                output.apply {
                    output.writeSmallInt(id)
                    write(key.uncheckedCast())
                }
            }
            id
        }
        encoder.writeSmallInt(id)
    }

    private
    fun doWriteString(key: String) {
        output.writeByte(1)
        output.writeString(key)
    }

    private
    fun doWriteClass(key: Class<*>) {
        output.writeByte(2)
        classEncoder.run {
            output.encodeClass(key)
        }
    }

    private
    fun doWriteClassLoader(key: ClassLoader) {
        output.writeByte(3)
        classEncoder.run {
            output.encodeClassLoader(key)
        }
    }
}
