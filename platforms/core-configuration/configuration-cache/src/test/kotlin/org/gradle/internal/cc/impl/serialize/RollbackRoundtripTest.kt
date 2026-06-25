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

import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.serialize.beans.services.DefaultBeanStateWriterLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

class RollbackRoundtripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `commit keeps the region and is byte-identical to writing it directly`() {
        val withScope = write {
            writeString("a")
            val scope = beginRollbackScope()
            writeString("b")
            scope.commit()
            writeString("c")
        }
        val direct = write {
            writeString("a")
            writeString("b")
            writeString("c")
        }
        assertArrayEquals(direct, withScope)
        assertEquals(listOf("a", "b", "c"), readStrings(withScope, 3))
    }

    @Test
    fun `rollback drops the region and is byte-identical to never writing it`() {
        val withScope = write {
            writeString("a")
            val scope = beginRollbackScope()
            writeString("b")
            scope.rollback()
            writeString("c")
        }
        val direct = write {
            writeString("a")
            writeString("c")
        }
        assertArrayEquals(direct, withScope)
        assertEquals(listOf("a", "c"), readStrings(withScope, 2))
    }

    @Test
    fun `commit relays a region that overflowed the in-memory buffer`() {
        val long = "a-region-that-exceeds-the-tiny-buffer".repeat(8)
        val bytes = write(bufferSize = 4) {
            writeString("a")
            val scope = beginRollbackScope()
            writeString(long)
            scope.commit()
            writeString("c")
        }
        assertEquals(listOf("a", long, "c"), readStrings(bytes, 3))
    }

    @Test
    fun `rollback drops a region that overflowed the in-memory buffer`() {
        val long = "a-region-that-exceeds-the-tiny-buffer".repeat(8)
        val bytes = write(bufferSize = 4) {
            writeString("a")
            val scope = beginRollbackScope()
            writeString(long)
            scope.rollback()
            writeString("c")
        }
        assertEquals(listOf("a", "c"), readStrings(bytes, 2))
    }

    @Test
    fun `overflow spools to an encrypted temp file and relays it correctly on commit`() {
        val long = "secret-payload-".repeat(64)
        val xorKey = 0x5A.toByte()
        val store = TempFileSpoolStore(
            tmp.newFile("work.bin"),
            { XorOutputStream(it, xorKey) },
            { XorInputStream(it, xorKey) }
        )
        val bytes = write(bufferSize = 4, spoolStore = store) {
            writeString("a")
            val scope = beginRollbackScope()
            writeString(long)
            scope.commit()
            writeString("c")
        }
        assertEquals(listOf("a", long, "c"), readStrings(bytes, 3))
    }

    @Test
    fun `supports successive commit and rollback scopes`() {
        val bytes = write(bufferSize = 4) {
            writeString("a")
            beginRollbackScope().also { writeString("keep") }.commit()
            beginRollbackScope().also { writeString("drop") }.rollback()
            writeString("b")
        }
        assertEquals(listOf("a", "keep", "b"), readStrings(bytes, 3))
    }

    private
    fun write(
        bufferSize: Int = SpillingOutputStream.DEFAULT_BUFFER_SIZE,
        spoolStore: SpoolStore = InMemorySpoolStore(),
        block: WriteContext.() -> Unit
    ): ByteArray {
        val main = ByteArrayOutputStream()
        val spilling = SpillingOutputStream(main, spoolStore, bufferSize)
        val encoder = KryoBackedEncoder(spilling)
        val context = writeContextForTesting(encoder, EncoderRollback(encoder, spilling))
        context.useToRun { block() }
        return main.toByteArray()
    }

    private
    fun readStrings(bytes: ByteArray, count: Int): List<String> =
        KryoBackedDecoder(ByteArrayInputStream(bytes)).let { decoder ->
            (1..count).map { decoder.readString() }
        }

    private
    fun writeContextForTesting(encoder: KryoBackedEncoder, rollback: EncoderRollback) =
        org.gradle.internal.serialize.graph.DefaultWriteContext(
            codec = mock<Codec<Any?>>(),
            encoder = encoder,
            classEncoder = DefaultClassEncoder(mock()),
            beanStateWriterLookup = DefaultBeanStateWriterLookup(),
            isIntegrityCheckEnabled = false,
            logger = mock(),
            tracer = null,
            problemsListener = mock(),
            rollbackSupport = rollback
        )

    private
    class InMemorySpoolStore : SpoolStore {
        override fun newSpool(): Spool = object : Spool {
            private val bytes = ByteArrayOutputStream()
            override fun outputStream(): OutputStream = bytes
            override fun inputStream(): InputStream = ByteArrayInputStream(bytes.toByteArray())
            override fun close() = Unit
        }
    }

    private
    class XorOutputStream(out: OutputStream, private val key: Byte) : FilterOutputStream(out) {
        override fun write(b: Int) = out.write((b.toByte().toInt() xor key.toInt()) and 0xFF)
        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) write(b[i].toInt())
        }
    }

    private
    class XorInputStream(input: InputStream, private val key: Byte) : FilterInputStream(input) {
        override fun read(): Int {
            val v = `in`.read()
            return if (v < 0) v else (v.toByte().toInt() xor key.toInt()) and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = `in`.read(b, off, len)
            for (i in off until off + (if (n < 0) 0 else n)) {
                b[i] = (b[i].toInt() xor key.toInt()).toByte()
            }
            return n
        }
    }
}
