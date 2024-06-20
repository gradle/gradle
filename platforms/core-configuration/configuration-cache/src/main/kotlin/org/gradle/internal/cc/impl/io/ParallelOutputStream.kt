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

package org.gradle.internal.cc.impl.io

import org.gradle.internal.cc.base.debug
import org.gradle.internal.cc.base.logger
import org.gradle.internal.extensions.stdlib.useToRun
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


internal
object ParallelOutputStream {

    fun of(createOutputStream: () -> OutputStream): OutputStream {

        val buffers = ByteBufferPool()
        val ready = ArrayBlockingQueue<ByteBuffer>(buffers.size)
        val writer = thread(name = "CC writer", isDaemon = true, priority = Thread.NORM_PRIORITY - 1) {
            // consider sharing a single (Gradle) thread among all stream writers
            try {
                createOutputStream().useToRun {
                    val outputChannel = Channels.newChannel(this)
                    while (true) {
                        val buffer = ready.takeWithTimeout()
                        if (!buffer.hasRemaining()) {
                            /** client is signaling end of stream
                             * see [QueuedOutputStream.close]
                             **/
                            break
                        }
                        try {
                            outputChannel.write(buffer)
                        } finally {
                            // always return the buffer
                            buffers.put(buffer)
                        }
                    }
                }
            } catch (e: Exception) {
                buffers.fail(e)
            }
            logger.debug { "${javaClass.name} writer ${Thread.currentThread()} finished." }
        }
        return QueuedOutputStream(buffers, ready) {
            writer.join()
        }
    }
}


internal
class QueuedOutputStream(
    private val buffers: ByteBufferPool,
    private val ready: ArrayBlockingQueue<ByteBuffer>,
    private val onClose: () -> Unit,
) : OutputStream() {

    private
    var buffer = buffers.take()

    override fun write(b: ByteArray, off: Int, len: Int) {
        writeByteArray(b, off, len)
    }

    override fun write(b: Int) {
        buffer.put(b.toByte())
        maybeFlush()
    }

    override fun close() {
        // send remaining data
        if (buffer.position() > 0) {
            sendBuffer()
            takeNextBuffer()
        }
        // send a last empty buffer to signal the end
        sendBuffer()
        onClose()
        buffers.rethrowFailureIfAny()
        super.close()
    }

    private
    tailrec fun writeByteArray(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) {
            return
        }
        val remaining = buffer.remaining()
        if (remaining > len) {
            putByteArrayAndFlush(b, off, len)
        } else {
            putByteArrayAndFlush(b, off, remaining)
            writeByteArray(b, off + remaining, len - remaining)
        }
    }

    private
    fun putByteArrayAndFlush(b: ByteArray, off: Int, len: Int) {
        buffer.put(b, off, len)
        maybeFlush()
    }

    private
    fun maybeFlush() {
        if (!buffer.hasRemaining()) {
            sendBuffer()
            takeNextBuffer()
        }
    }

    private
    fun sendBuffer() {
        buffer.flip()
        ready.put(buffer)
    }

    private
    fun takeNextBuffer() {
        buffer = buffers.take()
    }
}


internal
class ByteBufferPool {

    companion object {

        val bufferCount = System.getProperty("org.gradle.configuration-cache.internal.buffer-count", null)?.toInt()
            ?: 1024

        val bufferCapacity = System.getProperty("org.gradle.configuration-cache.internal.buffer-capacity", null)?.toInt()
            ?: (32 * 1024)

        val timeoutMinutes: Long = System.getProperty("org.gradle.configuration-cache.internal.buffer-timeout-seconds", null)?.toLong()
            ?: 30L /* stream can be kept open during the whole configuration phase */
    }

    private
    val buffers = ArrayBlockingQueue<ByteBuffer>(bufferCount).apply {
        while (remainingCapacity() > 0) {
            put(ByteBuffer.allocate(bufferCapacity))
        }
    }

    private
    val failure = AtomicReference<Exception>(null)

    val size: Int
        get() {
            rethrowFailureIfAny()
            return bufferCount
        }

    fun put(buffer: ByteBuffer) {
        rethrowFailureIfAny()
        buffer.flip()
        buffers.offerWithTimeout(buffer)
    }

    fun take(): ByteBuffer {
        rethrowFailureIfAny()
        return buffers.takeWithTimeout()
    }

    fun fail(e: Exception) {
        failure.set(e)
    }

    fun rethrowFailureIfAny() {
        failure.get()?.let {
            throw it
        }
    }
}


private
fun timeout(): Nothing = throw TimeoutException("Writer thread timed out.")


private
fun ArrayBlockingQueue<ByteBuffer>.offerWithTimeout(buffer: ByteBuffer) {
    if (!offer(buffer, ByteBufferPool.timeoutMinutes, TimeUnit.MINUTES)) {
        timeout()
    }
}


private
fun ArrayBlockingQueue<ByteBuffer>.takeWithTimeout() =
    poll(ByteBufferPool.timeoutMinutes, TimeUnit.MINUTES) ?: timeout()
