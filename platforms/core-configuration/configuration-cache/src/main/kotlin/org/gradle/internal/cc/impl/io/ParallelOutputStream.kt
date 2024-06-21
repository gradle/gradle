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
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


internal
object ParallelOutputStream {

    // By default, move 32 bytes at a time to the writer thread.
    val bufferCapacity = System.getProperty("org.gradle.configuration-cache.internal.buffer-capacity", null)?.toInt()
        ?: (1 * 32)

    // By default, keep at most 32MB in memory if the producer ends up being faster than the writer thread.
    private
    val bufferCount = System.getProperty("org.gradle.configuration-cache.internal.buffer-count", null)?.toInt()
        ?: (1024 * 1024)

    fun of(createOutputStream: () -> OutputStream): OutputStream {
        val failure = AtomicReference<Throwable>(null)
        val ready = LinkedBlockingQueue<ByteArray>(bufferCount)
        val writer = thread(name = "CC writer", isDaemon = true, priority = Thread.NORM_PRIORITY - 1) {
            // consider sharing a single (Gradle) thread among all stream writers
            try {
                createOutputStream().useToRun {
                    while (true) {
                        val buffer = ready.poll(timeoutMinutes, TimeUnit.MINUTES)
                            ?: throw TimeoutException("Writer thread timed out.")
                        if (buffer.isEmpty()) {
                            /** client is signaling end of stream
                             * see [QueuedOutputStream.close]
                             **/
                            break
                        }
                        write(buffer)
                    }
                }
            } catch (e: Throwable) {
                failure.set(e)
            } finally {
                ready.clear()
            }
            logger.debug {
                "${javaClass.name} writer ${Thread.currentThread()} finished."
            }
        }
        return QueuedOutputStream(ready, failure) {
            writer.join()
        }
    }
}


internal
class QueuedOutputStream(
    private val ready: BlockingQueue<ByteArray>,
    private val failure: AtomicReference<Throwable>,
    private val onClose: () -> Unit,
) : OutputStream() {

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) {
            return
        }
        failure.rethrowIfPresent()
        ready.put(b.copyOfRange(off, off + len))
    }

    override fun write(b: Int) {
        failure.rethrowIfPresent()
        ready.put(
            ByteArray(1).apply {
                set(0, b.toByte())
            }
        )
    }

    override fun close() {
        // send a last empty buffer to signal the end
        ready.put(ByteArray(0))
        onClose()
        failure.rethrowIfPresent()
        super.close()
    }
}


private
fun AtomicReference<Throwable>.rethrowIfPresent() {
    get()?.let {
        throw it
    }
}

/** 30 minutes by default since the stream can be kept open during the whole configuration phase. */
private
val timeoutMinutes: Long = System.getProperty("org.gradle.configuration-cache.internal.buffer-timeout-minutes", null)?.toLong()
    ?: 30L
