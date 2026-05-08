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

import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SharedObjectDecoder
import org.gradle.internal.serialize.graph.SharedObjectEncoder
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


private const val EOF = -1

/**
 * This value encoder stores data into a build-wide context.
 */
class DefaultSharedObjectEncoder(
    private val globalContext: CloseableWriteContext
) : SharedObjectEncoder {

    private
    val values = ConcurrentHashMap<Any, Int>()

    private
    var nextId = AtomicInteger(1)

    override suspend fun <T : Any> write(writeContext: WriteContext, value: T, encode: suspend WriteContext.(T) -> Unit) {
        val id = values.computeIfAbsent(value) { _ ->
            val id = nextId.getAndIncrement()
            // write the id and value to the global context
            doWriteValue(id, value)
            id
        }
        // write the id to the client context
        writeContext.writeSmallInt(id)
    }

    private
    fun <T : Any> doWriteValue(id: Int, value: T) {
        globalContext.synchronized {
            runWriteOperation {
                writeSmallInt(id)
                write(value)
            }
        }
    }

    override fun close() {
        globalContext.synchronized {
            use {
                writeSmallInt(EOF)
            }
        }
    }
}

class DefaultSharedObjectDecoder(
    private val globalContext: CloseableReadContext,
    private val valueWaitTimeout: Duration = Duration.ofMinutes(1)
) : SharedObjectDecoder, AutoCloseable {

    enum class ReaderState {
        READY, STARTED, RUNNING, STOPPING, STOPPED
    }

    private
    val state = AtomicReference(ReaderState.READY)

    private
    val readFailure = AtomicReference<Throwable?>(null)

    private
    inner class FutureValue(private val id: Int) {

        private
        val latch = CountDownLatch(1)

        private
        @Volatile
        var value: Any? = null

        fun complete(v: Any) {
            value = v
            latch.countDown()
        }

        fun release() {
            latch.countDown()
        }

        fun get(): Any {
            if (Thread.currentThread() === reader) {
                throw IllegalStateException(
                    "Recursive shared-object decode detected for id=$id; the reader thread is waiting on itself. " +
                        "This is a bug in a codec — most likely a `readSharedObject` invocation reachable from " +
                        "another shared object's `decode`."
                )
            }
            val state = state.get()
            // Only await if the reading thread is still running.
            // This saves us a minute in case the reading code is broken and doesn't countDown() the latch properly.
            // See the null check below.
            if (state < ReaderState.STOPPED && !latch.await(valueWaitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                reader.join(TimeUnit.SECONDS.toMillis(5))
                throwIfReaderFailed()
                throw TimeoutException("Timeout while waiting for value (id=$id), state was $state")
            }
            throwIfReaderFailed()
            val result = value
            require(result != null) {
                // Reading thread hasn't written the value before completing/calling countDown(). This can only happen if the decoder has a bug.
                "State is: $state"
            }
            return result
        }
    }

    private
    fun throwIfReaderFailed() {
        readFailure.get()?.let { cause ->
            throw IllegalStateException("Failed to decode shared value", cause)
        }
    }

    private
    val values = ConcurrentHashMap<Int, Any>()

    /**
     * Reads all values available (and their ids) to build the pool of shared objects
     */
    //TODO-RC Use a Gradle managed facility instead of an ad-hoc thread
    private
    val reader = thread(start = false, isDaemon = true, name = "${this::class.simpleName} reader thread") {
        require(state.compareAndSet(ReaderState.STARTED, ReaderState.RUNNING)) {
            "Unexpected state: $state"
        }
        try {
            globalContext.run {
                projectRefResolver.withWaitingForProjectsAllowed {
                    while (state.get() == ReaderState.RUNNING) {
                        val id = readSmallInt()
                        if (id == EOF) {
                            stopReading()
                            break
                        }
                        val read = runReadOperation {
                            read()!!
                        }
                        values.compute(id) { _, value ->
                            when (value) {
                                is FutureValue -> value.complete(read)
                                else -> require(value == null)
                            }
                            read
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            readFailure.set(t)
            for (entry in values.values) {
                if (entry is FutureValue) entry.release()
            }
        } finally {
            state.set(ReaderState.STOPPED)
        }
    }

    private val ReadContext.projectRefResolver
        get() = ownerService<ProjectRefResolver>()

    private
    fun ReadContext.resolveValue(id: Int): Any {
        startReadingIfNeeded()
        require(id >= 0) {
            "id: $id - $this"
        }
        throwIfReaderFailed()
        return when (val existing = values.computeIfAbsent(id) { FutureValue(id) }) {
            is FutureValue -> {
                if (readFailure.get() != null) {
                    existing.release()
                }
                existing.get()
            }
            else -> existing
        }
    }

    private fun startReadingIfNeeded() {
        if (state.compareAndSet(ReaderState.READY, ReaderState.STARTED)) {
            reader.start()
        }
    }

    override fun close() {
        globalContext.use {
            stopReading()
            reader.join(TimeUnit.MINUTES.toMillis(1))
        }
    }

    private fun stopReading() {
        state.compareAndSet(ReaderState.RUNNING, ReaderState.STOPPING)
    }

    override suspend fun <T : Any> read(readContext: ReadContext, decode: suspend ReadContext.() -> T): T {
        val id = readContext.readSmallInt()
        return readContext.resolveValue(id).uncheckedCast()
    }
}

fun <T : Any, C : IsolateContext> C.synchronized(action: C.() -> T?) = synchronized(this) {
    action()
}
