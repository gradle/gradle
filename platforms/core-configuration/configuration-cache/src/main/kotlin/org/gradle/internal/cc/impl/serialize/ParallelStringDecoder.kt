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

import com.esotericsoftware.kryo.io.Input
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.graph.StringDecoder
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread


/**
 * Decodes deduplicated strings from a given stream produced by [ParallelStringEncoder].
 */
internal
class ParallelStringDecoder(stream: InputStream) : StringDecoder, AutoCloseable {

    private
    class FutureString {

        private
        val latch = CountDownLatch(1)

        private
        var string: String? = null

        fun complete(s: String) {
            string = s
            latch.countDown()
        }

        fun get(): String {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                throw TimeoutException("Timeout while waiting for string")
            }
            return string!!
        }
    }

    private
    val strings = ConcurrentHashMap<Int, Any>()

    private
    val reader = thread(isDaemon = true) {
        Input(stream).use { input ->
            while (true) {
                val id = input.readVarInt(true)
                if (id == 0) break

                val string = input.readString()
                strings.compute(id) { _, value ->
                    when (value) {
                        is FutureString -> value.complete(string)
                        else -> require(value == null)
                    }
                    string
                }
            }
        }
    }

    override fun readNullableString(decoder: Decoder): String? =
        when (val id = decoder.readSmallInt()) {
            0 -> null
            else -> doReadString(id)
        }

    override fun readString(decoder: Decoder): String =
        doReadString(decoder.readSmallInt())

    private
    fun doReadString(id: Int): String =
        when (val it = strings.computeIfAbsent(id) { FutureString() }) {
            is String -> it
            is FutureString -> it.get()
            else -> error("$it is unexpected")
        }

    override fun close() {
        reader.join(TimeUnit.MINUTES.toMillis(1))
    }
}
