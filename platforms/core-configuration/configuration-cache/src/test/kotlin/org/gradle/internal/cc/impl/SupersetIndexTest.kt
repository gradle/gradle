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

package org.gradle.internal.cc.impl

import org.gradle.internal.cc.impl.SupersetIndexLookup.IndexedVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


/**
 * Unit tests for [SupersetIndexLookup] selection logic and [SupersetIndexFile] persistence.
 */
class SupersetIndexTest {

    @Test
    fun `exact match preferred over strict superset`() {
        val variants = listOf(
            IndexedVariant("v1", listOf("a")),
            IndexedVariant("v2", listOf("a", "b", "c"))
        )
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a"))
        assertEquals("v1", chosen?.fullKey)
    }

    @Test
    fun `smallest superset wins when no exact match`() {
        val variants = listOf(
            IndexedVariant("big", listOf("a", "b", "c", "d")),
            IndexedVariant("small", listOf("a", "b"))
        )
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a"))
        assertEquals("small", chosen?.fullKey)
    }

    @Test
    fun `non-superset variants are ignored`() {
        val variants = listOf(
            IndexedVariant("v1", listOf("x", "y"))
        )
        assertNull(SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a")))
    }

    @Test
    fun `flagged variant is exact-match only`() {
        val flagged = IndexedVariant("flagged", listOf("a", "b", "c"), taskGraphAccessed = true)
        // Superset request: flagged should be excluded.
        assertNull(SupersetIndexLookup.selectBestMatch(listOf(flagged), requested = listOf("a")))
        // Exact request: flagged should match.
        val chosen = SupersetIndexLookup.selectBestMatch(listOf(flagged), requested = listOf("a", "b", "c"))
        assertEquals("flagged", chosen?.fullKey)
    }

    @Test
    fun `duplicates in request are ignored when matching as subsequence`() {
        val variants = listOf(
            IndexedVariant("v1", listOf("c", "a", "b"))
        )
        // [a, a, b] dedupes to [a, b], which is a subsequence of [c, a, b].
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a", "a", "b"))
        assertEquals("v1", chosen?.fullKey)
    }

    @Test
    fun `reversed same-set request does not match stored entry`() {
        // [a, b] stored. Request [b, a] is the same set but the request is not a
        // subsequence of the stored list, so it is neither an exact match nor a
        // (size-equal) subsequence superset — no compatible entry.
        val variants = listOf(
            IndexedVariant("v1", listOf("a", "b"))
        )
        assertNull(SupersetIndexLookup.selectBestMatch(variants, requested = listOf("b", "a")))
    }

    @Test
    fun `subset must appear as subsequence of stored entry`() {
        val variants = listOf(
            IndexedVariant("v1", listOf("a", "b", "c"))
        )
        // [a, c] preserves relative order from [a, b, c] — match.
        assertEquals("v1", SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a", "c"))?.fullKey)
        // [c, a] reverses order — miss.
        assertNull(SupersetIndexLookup.selectBestMatch(variants, requested = listOf("c", "a")))
    }

    @Test
    fun `empty variant list returns null`() {
        assertNull(SupersetIndexLookup.selectBestMatch(emptyList(), requested = listOf("a")))
    }

    @Test
    fun `round trip preserves variants`() {
        val tmp = java.io.File.createTempFile("supersetIndex", ".bin").also { it.deleteOnExit() }
        val file = SupersetIndexFile(tmp)
        val variants = listOf(
            IndexedVariant("k1", listOf("a", "b")),
            IndexedVariant("k2", listOf("a"), taskGraphAccessed = true)
        )
        file.write(variants)
        assertEquals(variants, file.read())
    }

    @Test
    fun `missing file reads as empty list`() {
        val tmp = java.io.File.createTempFile("supersetIndex", ".bin").also {
            it.delete()
            it.deleteOnExit()
        }
        assertEquals(emptyList<IndexedVariant>(), SupersetIndexFile(tmp).read())
    }

    @Test
    fun `unknown format version reads as empty list`() {
        val tmp = java.io.File.createTempFile("supersetIndex", ".bin").also { it.deleteOnExit() }
        // Write 4 zero bytes — won't match magic header.
        tmp.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        assertEquals(emptyList<IndexedVariant>(), SupersetIndexFile(tmp).read())
    }
}
