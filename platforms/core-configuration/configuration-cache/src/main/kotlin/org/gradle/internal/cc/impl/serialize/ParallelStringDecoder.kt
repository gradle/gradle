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

import org.gradle.internal.Try
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.graph.ClassDecoder
import org.gradle.internal.serialize.graph.StringDecoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
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
class ParallelStringDecoder(
    stream: InputStream,
    private val classDecoder: ClassDecoder,
) : StringDecoder, ClassDecoder, AutoCloseable {

    private
    class FutureValue {

        private
        val latch = CountDownLatch(1)

        private
        var value: Try<Any>? = null

        fun complete(v: Try<Any>) {
            value = v
            latch.countDown()
        }

        fun get(): Try<Any> {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                throw TimeoutException("Timeout while waiting for value")
            }
            return value!!
        }
    }

    private
    val values = ConcurrentHashMap<Int, Any>()

    private
    val reader = thread(isDaemon = true, contextClassLoader = Thread.currentThread().contextClassLoader) {
        try {
            KryoBackedDecoder(stream).use { input ->
                while (true) {
                    val id = input.readSmallInt()
                    if (id == 0) break

                    val value = Try.ofFailable { readValue(input) }
                    values.compute(id) { _, current ->
                        when (current) {
                            is FutureValue -> current.complete(value)
                            else -> require(current == null)
                        }
                        value
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private
    fun readValue(input: Decoder): Any =
        when (val tag = input.readByte().toInt()) {
            1 -> input.readString()
            2 -> classDecoder.run { input.decodeClass() }
            3 -> classDecoder.run { input.decodeClassLoader()!! }
            else -> error("unexpected value tag: $tag")
        }

    override fun readNullableString(decoder: Decoder): String? =
        doReadNullable(decoder)

    override fun readString(decoder: Decoder): String =
        doReadValue(decoder.readSmallInt())

    override fun Decoder.decodeClass(): Class<*> =
        doReadValue(readSmallInt())

    override fun Decoder.decodeClassLoader(): ClassLoader? =
        doReadNullable(this)

    override fun close() {
        reader.join(TimeUnit.MINUTES.toMillis(1))
    }

    private
    inline fun <reified T : Any> doReadNullable(decoder: Decoder): T? =
        when (val id = decoder.readSmallInt()) {
            0 -> null
            else -> doReadValue(id)
        }

    private
    inline fun <reified T : Any> doReadValue(id: Int): T =
        when (val it = values.computeIfAbsent(id) { FutureValue() }) {
            is Try<*> -> it.get().uncheckedCast()
            is FutureValue -> it.get().get().uncheckedCast()
            else -> error("$it is unexpected")
        }
}
