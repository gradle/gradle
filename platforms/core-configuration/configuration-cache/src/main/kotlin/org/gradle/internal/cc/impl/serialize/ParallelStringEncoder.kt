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


/**
 * Deduplicates and encodes strings to a separate stream in a thread-safe manner.
 */
internal
class ParallelStringEncoder(stream: OutputStream) : StringEncoder {

    companion object {
        const val NULL_STRING = 0
        const val EMPTY_STRING = 1
        const val FIRST_STRING_ID = 2
    }

    private
    val strings = ConcurrentHashMap<String, Int>()

    private
    var nextId = FIRST_STRING_ID // 0 => null, 1 => ""

    private
    val output = Output(stream)

    override fun writeNullableString(encoder: Encoder, string: CharSequence?) {
        if (string == null) {
            encoder.writeSmallInt(NULL_STRING)
        } else {
            writeString(encoder, string)
        }
    }

    override fun writeString(encoder: Encoder, string: CharSequence) {
        encoder.writeSmallInt(
            if (string.isEmpty()) EMPTY_STRING
            else strings.computeIfAbsent(string.toString(), ::doWriteString)
        )
    }

    override fun close() {
        synchronized(output) {
            output.writeString("") // EOF
            output.close()
        }
    }

    private
    fun doWriteString(key: String): Int =
        synchronized(output) {
            val id = nextId++
            output.writeString(key)
            id
        }
}
