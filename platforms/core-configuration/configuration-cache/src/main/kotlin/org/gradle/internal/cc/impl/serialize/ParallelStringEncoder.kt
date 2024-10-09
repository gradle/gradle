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

import com.esotericsoftware.kryo.io.Output
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.StringEncoder
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


/**
 * Deduplicates and encodes strings to a separate stream in a thread-safe manner.
 */
internal
class ParallelStringEncoder(stream: OutputStream) : StringEncoder {

    private
    val strings = ConcurrentHashMap<String, Int>()

    private
    var nextId = AtomicInteger(1)

    private
    val output = Output(stream)

    override fun writeNullableString(encoder: Encoder, string: CharSequence?) {
        if (string == null) {
            encoder.writeSmallInt(0)
        } else {
            writeString(encoder, string)
        }
    }

    override fun writeString(encoder: Encoder, string: CharSequence) {
        val id = strings.computeIfAbsent(string.toString()) { key ->
            val id = nextId.getAndIncrement()
            doWriteString(id, key)
            id
        }
        encoder.writeSmallInt(id)
    }

    override fun close() {
        output.writeStringId(0) // EOF
        output.close()
    }

    private
    fun doWriteString(id: Int, key: String) {
        synchronized(output) {
            output.writeStringId(id)
            output.writeString(key)
        }
    }

    private
    fun Output.writeStringId(id: Int) {
        writeVarInt(id, true)
    }
}
