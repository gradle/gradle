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
    private val globalContext: CloseableReadContext
) : SharedObjectDecoder, AutoCloseable {

    enum class ReaderState {
        READY, STARTED, RUNNING, STOPPING, STOPPED
    }

    private
    val state = AtomicReference(ReaderState.READY)

    private
    inner class FutureValue {

        private
        val latch = CountDownLatch(1)

        private
        @Volatile
        var value: Any? = null

        fun complete(v: Any) {
            value = v
            latch.countDown()
        }

        fun get(): Any {
            val state = state.get()
            // Only await if the reading thread is still running.
            // This saves us a minute in case the reading code is broken and doesn't countDown() the latch properly.
            // See the null check below.
            if (state < ReaderState.STOPPED && !latch.await(1, TimeUnit.MINUTES)) {
                throw TimeoutException("Timeout while waiting for value, state was $state")
            }
            val result = value
            require(result != null) {
                // Reading thread hasn't written the value before completing/calling countDown(). This can only happen if the decoder has a bug.
                "State is: $state"
            }
            return result
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
        return when (val existing = values.computeIfAbsent(id) { FutureValue() }) {
            is FutureValue -> existing.get()
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
