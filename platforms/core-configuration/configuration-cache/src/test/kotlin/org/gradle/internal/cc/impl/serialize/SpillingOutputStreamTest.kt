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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class SpillingOutputStreamTest {

    private
    val main = ByteArrayOutputStream()

    private
    val spoolStore = RecordingSpoolStore()

    private
    fun newStream(bufferSize: Int = 16) = SpillingOutputStream(main, spoolStore, bufferSize)

    @Test
    fun `passes through writes when no scope is open`() {
        val stream = newStream()
        stream.write("hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), main.toByteArray())
        assertEquals(5L, stream.committedBytes)
    }

    @Test
    fun `commit relays buffered bytes in order without touching disk`() {
        val stream = newStream(bufferSize = 16)
        stream.write("pre-".toByteArray())
        stream.beginSpill()
        stream.write("spilled".toByteArray())
        stream.commit()
        stream.write("-post".toByteArray())

        assertArrayEquals("pre-spilled-post".toByteArray(), main.toByteArray())
        assertEquals(0, spoolStore.created.size) // stayed in memory
    }

    @Test
    fun `rollback discards buffered bytes`() {
        val stream = newStream(bufferSize = 16)
        stream.write("pre-".toByteArray())
        stream.beginSpill()
        stream.write("discarded".toByteArray())
        stream.rollback()
        stream.write("post".toByteArray())

        assertArrayEquals("pre-post".toByteArray(), main.toByteArray())
        assertEquals(0, spoolStore.created.size)
    }

    @Test
    fun `commit relays overflowed bytes from the spool in order`() {
        val stream = newStream(bufferSize = 4)
        stream.write("pre-".toByteArray())
        stream.beginSpill()
        // exceeds the 4-byte buffer, so it overflows to a spool
        stream.write("a-long-spilled-region".toByteArray())
        stream.commit()
        stream.write("-post".toByteArray())

        assertArrayEquals("pre-a-long-spilled-region-post".toByteArray(), main.toByteArray())
        assertEquals(1, spoolStore.created.size)
        assertTrue("spool should be closed/deleted after commit", spoolStore.created.single().closed)
    }

    @Test
    fun `rollback discards overflowed bytes and deletes the spool`() {
        val stream = newStream(bufferSize = 4)
        stream.write("pre-".toByteArray())
        stream.beginSpill()
        stream.write("a-long-discarded-region".toByteArray())
        stream.rollback()
        stream.write("post".toByteArray())

        assertArrayEquals("pre-post".toByteArray(), main.toByteArray())
        assertEquals(1, spoolStore.created.size)
        assertTrue("spool should be closed/deleted after rollback", spoolStore.created.single().closed)
    }

    @Test
    fun `committedBytes ignores spilled bytes until commit`() {
        val stream = newStream(bufferSize = 4)
        stream.write("abcd".toByteArray())
        assertEquals(4L, stream.committedBytes)

        stream.beginSpill()
        stream.write("spilled-region".toByteArray())
        assertEquals(4L, stream.committedBytes) // tentative bytes not counted

        stream.commit()
        assertEquals(4L + "spilled-region".length, stream.committedBytes)
    }

    @Test
    fun `supports successive scopes after commit and rollback`() {
        val stream = newStream(bufferSize = 4)
        stream.beginSpill()
        stream.write("keep".toByteArray())
        stream.commit()

        stream.beginSpill()
        stream.write("drop".toByteArray())
        stream.rollback()

        stream.beginSpill()
        stream.write("keep2".toByteArray())
        stream.commit()

        assertArrayEquals("keepkeep2".toByteArray(), main.toByteArray())
    }

    @Test
    fun `beginSpill rejects nesting`() {
        val stream = newStream()
        stream.beginSpill()
        assertThrows(IllegalStateException::class.java) { stream.beginSpill() }
    }

    @Test
    fun `commit and rollback require an open scope`() {
        val stream = newStream()
        assertThrows(IllegalStateException::class.java) { stream.rollback() }
        assertThrows(IllegalStateException::class.java) { stream.commit() }
    }

    private
    class RecordingSpoolStore : SpoolStore {
        val created = mutableListOf<RecordingSpool>()
        override fun newSpool(): Spool = RecordingSpool().also { created.add(it) }
    }

    private
    class RecordingSpool : Spool {
        private val bytes = ByteArrayOutputStream()
        var closed = false
            private set

        override fun outputStream(): OutputStream = bytes

        override fun inputStream(): InputStream = ByteArrayInputStream(bytes.toByteArray())

        override fun close() {
            closed = true
        }
    }
}
