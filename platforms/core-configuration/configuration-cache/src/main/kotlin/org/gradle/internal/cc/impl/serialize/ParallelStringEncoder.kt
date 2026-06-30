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
import org.gradle.internal.cc.impl.serialize.ParallelStringEncoder.Companion.EMPTY_STRING_ID
import org.gradle.internal.cc.impl.serialize.ParallelStringEncoder.Companion.NULL_STRING_ID
import org.gradle.internal.collect.PersistentArray
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.StringEncoder
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


/**
 * Deduplicates and encodes strings to a separate stream in a thread-safe manner.
 *
 * The file produced is a sequence of non-empty strings, followed by an empty string that marks the end-of-file.
 *
 * Requests to write strings provide a client encoder and a string to write. This string encoder will do one of the following:
 *
 * - in case of [null][NULL_STRING_ID] or [empty strings][EMPTY_STRING_ID], simply write the corresponding special id into the client's encoder;
 * - if the non-empty string has not been seen before, write it to this file and write the new, sequentially assigned, id for it into the client's encoder;
 * - otherwise, the string has been written before, so just write the previously generated id for that string into the client's encoder.
 */
internal
class ParallelStringEncoder(stream: OutputStream) : StringEncoder {

    companion object {
        const val NULL_STRING_ID = 0
        const val EMPTY_STRING_ID = 1
        const val FIRST_STRING_ID = 2
    }

    private
    data class WriteQueue(
        val nextStringId: Int,
        val pending: PersistentArray<String>
    ) {
        fun offer(s: String): WriteQueue =
            WriteQueue(nextStringId + 1, pending + s)

        fun flush(): WriteQueue = when {
            pending.isEmpty() -> this

            else -> WriteQueue(nextStringId, PersistentArray.of())
        }
    }

    private
    val writeQueue = AtomicReference(
        WriteQueue(
            FIRST_STRING_ID, // 0 => null, 1 => ""
            PersistentArray.of()
        )
    )

    @Volatile
    private
    var writeFailure: Exception? = null

    private
    val writeThread = thread(priority = Thread.MIN_PRIORITY) {
        try {
            Output(stream).use { output ->
                var writing = true
                while (writing) {
                    val pending = writeQueue.getAndUpdate(WriteQueue::flush).pending
                    if (pending.isEmpty) {
                        Thread.onSpinWait()
                        continue
                    }
                    for (s in pending) {
                        output.writeString(s)
                        if (s.isEmpty()) {
                            writing = false
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            writeFailure = e;
        }
    }

    private
    val strings = ConcurrentHashMap<String, Int>()

    override fun writeNullableString(encoder: Encoder, string: CharSequence?) {
        if (string == null) {
            encoder.writeSmallInt(NULL_STRING_ID)
        } else {
            writeString(encoder, string)
        }
    }

    override fun writeString(encoder: Encoder, string: CharSequence) {
        encoder.writeSmallInt(
            if (string.isEmpty()) EMPTY_STRING_ID
            else strings.computeIfAbsent(string.toString(), ::doWriteString)
        )
    }

    override fun close() {
        doWriteString("")
        writeThread.join(TimeUnit.MINUTES.toMillis(1))
        writeFailure?.let {
            throw it
        }
    }

    private
    fun doWriteString(s: String): Int =
        writeQueue.getAndUpdate {
            it.offer(s)
        }.nextStringId
}
