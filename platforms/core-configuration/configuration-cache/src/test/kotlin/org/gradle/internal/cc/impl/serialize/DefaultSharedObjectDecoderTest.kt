/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.serialize.beans.services.test.beanStateReaderLookupForTesting
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SpecialDecoders
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


class DefaultSharedObjectDecoderTest {

    private val owner = IsolateOwners.OwnerHost(object : HostServiceProvider {
        private val resolver = ProjectRefResolver(mock())

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> service(serviceType: Class<T>): T = when (serviceType) {
            ProjectRefResolver::class.java -> resolver as T
            else -> error("unexpected service request: $serviceType")
        }
    })

    @Test
    fun `reader thread failure surfaces immediately to consumer with the original cause`() {
        val throwingCodec: Codec<Any?> = object : Codec<Any?> {
            override suspend fun WriteContext.encode(value: Any?) = Unit
            override suspend fun ReadContext.decode() = throw RuntimeException("boom")
        }

        val thrown = decodeWith(throwingCodec) { decoder, clientContext ->
            assertThrows(IllegalStateException::class.java) {
                clientContext.runReadOperation {
                    decoder.read(this) { error("not used") }
                }
            }
        }

        assertThat(thrown.message, containsString("Failed to decode shared value"))
        assertThat(thrown.cause, instanceOf(RuntimeException::class.java))
        assertThat(thrown.cause!!.message, equalTo("boom"))
    }

    @Test
    fun `consumer timing out before reader gets the reader's cause via the join grace`() {
        val slowFailingCodec: Codec<Any?> = object : Codec<Any?> {
            override suspend fun WriteContext.encode(value: Any?) = Unit
            override suspend fun ReadContext.decode(): Any? {
                Thread.sleep(300)
                throw RuntimeException("delayed boom")
            }
        }

        val thrown = decodeWith(slowFailingCodec, valueWaitTimeout = Duration.ofMillis(100)) { decoder, clientContext ->
            assertThrows(IllegalStateException::class.java) {
                clientContext.runReadOperation {
                    decoder.read(this) { error("not used") }
                }
            }
        }

        assertThat(thrown.message, containsString("Failed to decode shared value"))
        assertThat(thrown.cause, instanceOf(RuntimeException::class.java))
        assertThat(thrown.cause!!.message, equalTo("delayed boom"))
    }

    @Test(timeout = 30_000)
    fun `consumer asking for an id the reader never writes is released when the reader completes normally`() {
        val readerMayComplete = CountDownLatch(1)

        val codec: Codec<Any?> = object : Codec<Any?> {
            override suspend fun WriteContext.encode(value: Any?) = Unit
            override suspend fun ReadContext.decode(): Any {
                readerMayComplete.await()
                return "foo"
            }
        }

        val globalContext = readContextOver(writeBytes { writeSmallInt(1); writeSmallInt(-1) }, codec).also {
            it.push(owner, codec)
        }
        val decoder = DefaultSharedObjectDecoder(globalContext, valueWaitTimeout = Duration.ofSeconds(30))

        val thrown = AtomicReference<Throwable?>()
        val elapsedMs = AtomicReference(0L)
        decoder.use {
            val clientContext = readContextOver(
                writeBytes { writeSmallInt(11) },
                codec,
                SpecialDecoders(sharedObjectDecoder = decoder)
            ).also { it.push(owner, codec) }

            val consumer = thread(name = "test-consumer") {
                val start = System.nanoTime()
                try {
                    clientContext.runReadOperation {
                        decoder.read(this) { error("not used") }
                    }
                } catch (t: Throwable) {
                    thrown.set(t)
                } finally {
                    elapsedMs.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                }
            }

            awaitThreadParked(consumer)
            readerMayComplete.countDown()

            consumer.join(TimeUnit.SECONDS.toMillis(10))
            assertTrue("consumer thread did not finish - it is likely stuck on the value wait timeout", !consumer.isAlive)
        }

        val failure = thrown.get()
        assertThat(failure, notNullValue())
        assertThat(failure, instanceOf(RuntimeException::class.java))
        assertThat(failure, not(instanceOf(TimeoutException::class.java)))
        assertTrue("consumer took ${elapsedMs.get()}ms - it waited for the value timeout", elapsedMs.get() < 5_000)
    }

    private
    fun awaitThreadParked(t: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (t.state != Thread.State.TIMED_WAITING && t.state != Thread.State.WAITING) {
            if (System.nanoTime() > deadline) fail("thread ${t.name} never parked, state=${t.state}")
            Thread.sleep(5)
        }
    }

    private
    fun <T> decodeWith(
        codec: Codec<Any?>,
        valueWaitTimeout: Duration = Duration.ofMinutes(1),
        action: (DefaultSharedObjectDecoder, DefaultReadContext) -> T
    ): T {
        val globalContext = readContextOver(writeBytes { writeSmallInt(1) }, codec).also {
            it.push(owner, codec)
        }
        DefaultSharedObjectDecoder(globalContext, valueWaitTimeout = valueWaitTimeout).use { decoder ->
            val clientContext = readContextOver(
                writeBytes { writeSmallInt(1) },
                codec,
                SpecialDecoders(sharedObjectDecoder = decoder)
            ).also {
                it.push(owner, codec)
            }
            return action(decoder, clientContext)
        }
    }

    private
    fun writeBytes(write: KryoBackedEncoder.() -> Unit): ByteArray =
        ByteArrayOutputStream().also { out ->
            KryoBackedEncoder(out).use { it.write() }
        }.toByteArray()

    private
    fun readContextOver(
        bytes: ByteArray,
        codec: Codec<Any?>,
        specialDecoders: SpecialDecoders = SpecialDecoders()
    ): DefaultReadContext =
        DefaultReadContext(
            codec = codec,
            decoder = KryoBackedDecoder(ByteArrayInputStream(bytes)),
            beanStateReaderLookup = beanStateReaderLookupForTesting(),
            isIntegrityCheckEnabled = false,
            logger = mock(),
            problemsListener = mock(),
            classDecoder = DefaultClassDecoder(mock(), mock()),
            specialDecoders = specialDecoders
        )
}
