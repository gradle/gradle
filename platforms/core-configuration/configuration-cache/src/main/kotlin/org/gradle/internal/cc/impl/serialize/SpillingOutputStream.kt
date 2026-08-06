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

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream


/**
 * A transient store for bytes spilled while a rollback scope is open.
 *
 * The bytes written to [outputStream] are the same bytes that can later be read back from
 * [inputStream]. Implementations that persist the spool encrypt on write and decrypt on read, so
 * that plaintext never reaches disk.
 */
internal
interface Spool : Closeable {
    fun outputStream(): OutputStream

    fun inputStream(): InputStream

    /**
     * Releases the spool, deleting any backing storage.
     */
    override fun close()
}


/**
 * Creates a fresh [Spool] for a rollback scope.
 */
internal
fun interface SpoolStore {
    fun newSpool(): Spool
}


/**
 * An [OutputStream] that can divert everything written after a [mark][beginSpill] away from the
 * main destination, so it can later be relayed verbatim ([commit]) or dropped ([rollback]).
 *
 * While diverted, bytes are held in a small in-memory buffer and only overflow to a (potentially
 * encrypted) [Spool] once the buffer is exceeded, so small rollback regions never touch disk.
 *
 * Nesting is not supported: [beginSpill] must be balanced by exactly one [commit] or [rollback]
 * before it can be called again.
 *
 * This stream does not own the buffering of its writer (e.g. a Kryo `Output`). The writer must
 * flush into this stream before [beginSpill] (so pre-mark bytes reach the main destination) and
 * before [commit] (so the whole region is captured); on [rollback] the writer must instead discard
 * its own un-flushed tail.
 */
internal
class SpillingOutputStream(
    private val main: OutputStream,
    private val spoolStore: SpoolStore,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
) : OutputStream() {

    private
    enum class Mode { PASSTHROUGH, BUFFERING, SPILLED }

    private
    var mode = Mode.PASSTHROUGH

    private
    var buffer: ByteArrayOutputStream? = null

    private
    var spool: Spool? = null

    private
    var spoolSink: OutputStream? = null

    /**
     * Number of bytes durably written to [main] (pre-mark bytes plus committed regions). Diverted
     * bytes are not counted until they are committed. Used to report a meaningful write position.
     */
    var committedBytes: Long = 0
        private set

    override fun write(b: Int) {
        when (mode) {
            Mode.PASSTHROUGH -> writeToMain(b)
            Mode.BUFFERING -> {
                if (buffer!!.size() + 1 > bufferSize) {
                    overflow()
                    spoolSink!!.write(b)
                } else {
                    buffer!!.write(b)
                }
            }

            Mode.SPILLED -> spoolSink!!.write(b)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        when (mode) {
            Mode.PASSTHROUGH -> writeToMain(b, off, len)
            Mode.BUFFERING -> {
                if (buffer!!.size() + len > bufferSize) {
                    overflow()
                    spoolSink!!.write(b, off, len)
                } else {
                    buffer!!.write(b, off, len)
                }
            }

            Mode.SPILLED -> spoolSink!!.write(b, off, len)
        }
    }

    override fun flush() {
        when (mode) {
            Mode.PASSTHROUGH -> main.flush()
            // Nothing to flush downstream while buffering: the bytes are tentative.
            Mode.BUFFERING -> Unit
            Mode.SPILLED -> spoolSink!!.flush()
        }
    }

    override fun close() {
        // The main stream owns the underlying file. Abandon any open spool first.
        try {
            spoolSink?.close()
        } finally {
            try {
                spool?.close()
            } finally {
                main.close()
            }
        }
    }

    /**
     * Starts diverting subsequent writes so they can be committed or rolled back.
     */
    fun beginSpill() {
        check(mode == Mode.PASSTHROUGH) { "Already spilling; nested rollback scopes are not supported." }
        buffer = ByteArrayOutputStream()
        mode = Mode.BUFFERING
    }

    /**
     * Relays everything written since [beginSpill] into [main], in order, then returns to passthrough.
     */
    fun commit() {
        when (mode) {
            Mode.BUFFERING -> buffer!!.let { writeToMain(it.toByteArray()) }
            Mode.SPILLED -> {
                spoolSink!!.close()
                spoolSink = null
                spool!!.inputStream().use { relayToMain(it) }
            }

            Mode.PASSTHROUGH -> error("Not spilling.")
        }
        cleanupSpill()
    }

    /**
     * Discards everything written since [beginSpill], then returns to passthrough.
     */
    fun rollback() {
        check(mode != Mode.PASSTHROUGH) { "Not spilling." }
        spoolSink?.close()
        spoolSink = null
        cleanupSpill()
    }

    private
    fun overflow() {
        val newSpool = spoolStore.newSpool()
        val sink = newSpool.outputStream()
        buffer!!.writeTo(sink)
        buffer = null
        spool = newSpool
        spoolSink = sink
        mode = Mode.SPILLED
    }

    private
    fun cleanupSpill() {
        spool?.close()
        spool = null
        spoolSink = null
        buffer = null
        mode = Mode.PASSTHROUGH
    }

    private
    fun relayToMain(input: InputStream) {
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            writeToMain(chunk, 0, read)
        }
    }

    private
    fun writeToMain(b: Int) {
        main.write(b)
        committedBytes++
    }

    private
    fun writeToMain(b: ByteArray) = writeToMain(b, 0, b.size)

    private
    fun writeToMain(b: ByteArray, off: Int, len: Int) {
        main.write(b, off, len)
        committedBytes += len
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
