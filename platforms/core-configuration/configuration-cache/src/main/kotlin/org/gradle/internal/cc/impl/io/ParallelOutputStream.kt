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

import org.gradle.internal.cc.base.logger
import org.gradle.internal.extensions.core.debug
import java.io.OutputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.math.roundToInt


/**
 * Factory for an [OutputStream] decorator that offloads the writing to a separate thread.
 */
internal
object ParallelOutputStream {

    /**
     * See [ByteBufferPool.chunkSize].
     */
    val recommendedBufferSize: Int
        get() = ByteBufferPool.chunkSize

    /**
     * Returns an [OutputStream] that offloads writing to the stream returned by [outputStreamFactory]
     * to a separate thread. The returned stream can only be written to from a single thread at a time.
     *
     * Note that [outputStreamFactory] will be called in the writing thread.
     *
     * @see QueuedOutputStream
     * @see ByteBufferPool
     */
    fun of(
        readyQ: Queue<ByteBuffer> = ConcurrentLinkedQueue(),
        outputStreamFactory: () -> OutputStream,
    ): OutputStream {
        val chunks = ByteBufferPool()
        val writer = thread(name = "CC writer", isDaemon = true, priority = Thread.NORM_PRIORITY) {
            try {
                outputStreamFactory().use { outputStream ->
                    while (true) {
                        val chunk = readyQ.poll()
                        if (chunk == null) {
                            // give the producer another chance
                            Thread.yield()
                            continue
                        }
                        val position = chunk.position()
                        if (position == 0) {
                            /** producer is signaling end of stream
                             * see [QueuedOutputStream.close]
                             **/
                            break
                        }
                        try {
                            outputStream.write(chunk.array(), 0, position)
                        } finally {
                            // always return the chunk to the pool
                            rewind(chunk)
                            chunks.put(chunk)
                        }
                    }
                }
            } catch (e: Exception) {
                chunks.fail(e)
            } finally {
                // in case of failure, this releases some memory until the
                // producer realizes there was a failure
                readyQ.clear()
            }
            logger.debug {
                "${javaClass.name} writer ${Thread.currentThread()} finished."
            }
        }
        return QueuedOutputStream(chunks, readyQ) {
            writer.join()
        }
    }

    private
    fun rewind(chunk: Buffer /* for compatibility with Java 8 */) {
        chunk.rewind()
    }
}


/**
 * An [OutputStream] implementation that writes to buffers taken from a [ByteBufferPool]
 * and posts them to the given [ready queue][readyQ] when they are full.
 */
private
class QueuedOutputStream(
    private val chunks: ByteBufferPool,
    private val readyQ: Queue<ByteBuffer>,
    private val onClose: () -> Unit,
) : OutputStream() {

    private
    var chunk = chunks.take()

    override fun write(b: ByteArray, off: Int, len: Int) {
        writeByteArray(b, off, len)
    }

    override fun write(b: Int) {
        chunk.put(b.toByte())
        maybeFlush()
    }

    override fun close() {
        // send remaining data
        if (chunk.position() > 0) {
            sendChunk()
            takeNextChunk()
        }
        // send a last empty chunk to signal the end
        sendChunk()
        onClose()
        chunks.rethrowFailureIfAny()
        super.close()
    }

    private
    tailrec fun writeByteArray(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) {
            return
        }
        val remaining = chunk.remaining()
        if (remaining >= len) {
            putByteArrayAndFlush(b, off, len)
        } else {
            putByteArrayAndFlush(b, off, remaining)
            writeByteArray(b, off + remaining, len - remaining)
        }
    }

    private
    fun putByteArrayAndFlush(b: ByteArray, off: Int, len: Int) {
        chunk.put(b, off, len)
        maybeFlush()
    }

    private
    fun maybeFlush() {
        if (!chunk.hasRemaining()) {
            sendChunk()
            takeNextChunk()
        }
    }

    private
    fun sendChunk() {
        readyQ.offer(chunk)
    }

    private
    fun takeNextChunk() {
        chunk = chunks.take()
    }
}


/**
 * Manages a pool of chunks of fixed [size][ByteBufferPool.chunkSize] allocated on-demand
 * upto a [fixed maximum][ByteBufferPool.maxChunks].
 */
class ByteBufferPool {

    companion object {

        /**
         * How many bytes are transferred, at a time, from the producer to the writer thread.
         *
         * The smaller the number the more parallelism between producer and writer (but also greater the synchronization overhead).
         */
        val chunkSize = System.getProperty("org.gradle.configuration-cache.internal.chunk-size", null)?.toInt()
            ?: 4096

        /**
         * Maximum number of chunks to be allocated.
         *
         * Determines the maximum memory working set: [maxChunks] * [chunkSize].
         * The default maximum working set is `16MB`.
         */
        val maxChunks = System.getProperty("org.gradle.configuration-cache.internal.max-chunks", null)?.toInt()
            ?: 4096

        val chunkTimeoutMinutes: Long = System.getProperty("org.gradle.configuration-cache.internal.chunk-timeout-minutes", null)?.toLong()
            ?: 30L /* stream can be kept open during the whole configuration phase */
    }

    private
    val chunks = ArrayBlockingQueue<ByteBuffer>(maxChunks)

    private
    var chunksToAllocate = maxChunks

    @Volatile
    private
    var failure: Exception? = null

    fun put(chunk: ByteBuffer) {
        require(chunk.position() == 0 && chunk.remaining() == chunk.limit())
        require(chunks.offer(chunk))
    }

    private
    val chunkReuseThreshold = (maxChunks * 0.9).roundToInt()

    fun take(): ByteBuffer {
        rethrowFailureIfAny()
        val remainingAllocations = chunksToAllocate
        if (remainingAllocations > 0) {
            if (remainingAllocations < chunkReuseThreshold) {
                // try to reuse chunks past some threshold
                // to amortize the cost of locking the chunks queue
                val reused = chunks.poll()
                if (reused != null) {
                    return reused
                }
            }
            --chunksToAllocate
            return ByteBuffer.allocate(chunkSize)
        }
        return chunks.poll(chunkTimeoutMinutes, TimeUnit.MINUTES)
            ?: throw TimeoutException("Timed out while waiting for a chunk.")
    }

    fun fail(e: Exception) {
        failure = e
    }

    fun rethrowFailureIfAny() {
        failure?.let {
            throw it
        }
    }
}
