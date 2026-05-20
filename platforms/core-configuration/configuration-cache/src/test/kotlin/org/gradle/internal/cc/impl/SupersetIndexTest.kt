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
 * <p>
 * Most tests use single-project shapes where each CLI token resolves to a single
 * identity path (1:1), so the helper [v] mirrors the CLI list into the
 * entry-task identity-path list. Multi-project (non-1:1) shapes are exercised
 * explicitly where needed.
 */
class SupersetIndexTest {

    private
    fun v(
        fullKey: String,
        cliTokens: List<String>,
        entryTaskIdentityPaths: List<String> = cliTokens,
        taskGraphAccessed: Boolean = false
    ) = IndexedVariant(fullKey, cliTokens, entryTaskIdentityPaths, taskGraphAccessed)

    @Test
    fun `exact match preferred over strict superset`() {
        val variants = listOf(
            v("v1", listOf("a")),
            v("v2", listOf("a", "b", "c"))
        )
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a"))
        assertEquals("v1", chosen?.fullKey)
    }

    @Test
    fun `smallest superset wins when no exact match`() {
        val variants = listOf(
            v("big", listOf("a", "b", "c", "d")),
            v("small", listOf("a", "b"))
        )
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a"))
        assertEquals("small", chosen?.fullKey)
    }

    @Test
    fun `non-superset variants are ignored`() {
        val variants = listOf(v("v1", listOf("x", "y")))
        assertNull(SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a")))
    }

    @Test
    fun `flagged variant is exact-match only`() {
        val flagged = v("flagged", listOf("a", "b", "c"), taskGraphAccessed = true)
        // Superset request: flagged should be excluded.
        assertNull(SupersetIndexLookup.selectBestMatch(listOf(flagged), requested = listOf("a")))
        // Exact request: flagged should match.
        val chosen = SupersetIndexLookup.selectBestMatch(listOf(flagged), requested = listOf("a", "b", "c"))
        assertEquals("flagged", chosen?.fullKey)
    }

    @Test
    fun `bare and absolute CLI tokens do not collide`() {
        // Two stored entries for the same single-token request — bare and absolute.
        // Selection by verbatim CLI matching must keep them distinct: a bare-`d`
        // lookup never picks the absolute-`:d` entry, and vice versa.
        val bareStored = v("bare", listOf("d"))
        val absoluteStored = v("absolute", listOf(":d"))
        val variants = listOf(bareStored, absoluteStored)
        assertEquals("bare", SupersetIndexLookup.selectBestMatch(variants, listOf("d"))?.fullKey)
        assertEquals("absolute", SupersetIndexLookup.selectBestMatch(variants, listOf(":d"))?.fullKey)
    }

    @Test
    fun `non-one-to-one variant is exact-match only`() {
        // Multi-project bare name: one CLI token resolved to two identity paths.
        // Subset matches would have an ambiguous CLI→identity mapping, so the
        // matcher must restrict this entry to exact matches.
        val multiProject = IndexedVariant(
            fullKey = "mp",
            cliTokens = listOf("d"),
            entryTaskIdentityPaths = listOf(":d", ":sub:d")
        )
        // Exact match still allowed.
        assertEquals("mp", SupersetIndexLookup.selectBestMatch(listOf(multiProject), listOf("d"))?.fullKey)
        // A request that would normally subset-match (only one token of a multi-token entry)
        // — here we flip the setup so the multi-project entry hosts the request as a subset.
        val biggerMultiProject = IndexedVariant(
            fullKey = "mp2",
            cliTokens = listOf("d", "e"),
            entryTaskIdentityPaths = listOf(":d", ":sub:d", ":e")
        )
        // `d` is a subsequence of `[d, e]`, but mp2 is non-1:1 → must be excluded from supersets.
        assertNull(SupersetIndexLookup.selectBestMatch(listOf(biggerMultiProject), requested = listOf("d")))
    }

    @Test
    fun `duplicates in request are ignored when matching as subsequence`() {
        val variants = listOf(v("v1", listOf("c", "a", "b")))
        // [a, a, b] dedupes to [a, b], which is a subsequence of [c, a, b].
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a", "a", "b"))
        assertEquals("v1", chosen?.fullKey)
    }

    @Test
    fun `reversed same-set request does not match stored entry`() {
        // [a, b] stored. Request [b, a] is the same set but the request is not a
        // subsequence of the stored list, so no compatible entry.
        val variants = listOf(v("v1", listOf("a", "b")))
        assertNull(SupersetIndexLookup.selectBestMatch(variants, requested = listOf("b", "a")))
    }

    @Test
    fun `subset must appear as subsequence of stored entry`() {
        val variants = listOf(v("v1", listOf("a", "b", "c")))
        assertEquals("v1", SupersetIndexLookup.selectBestMatch(variants, requested = listOf("a", "c"))?.fullKey)
        assertNull(SupersetIndexLookup.selectBestMatch(variants, requested = listOf("c", "a")))
    }

    @Test
    fun `empty variant list returns null`() {
        assertNull(SupersetIndexLookup.selectBestMatch(emptyList(), requested = listOf("a")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false when no edges`() {
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(emptyMap(), droppedIdentityPaths = setOf(":a")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false on empty dropped set`() {
        // Exact match — no pruning.
        val edges = mapOf(":b" to listOf(":a"))
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(edges, droppedIdentityPaths = emptySet()))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false when edge stays within retained set`() {
        // Edge `:c mustRunAfter :b`; dropped only contains `:a`. Both endpoints retained.
        val edges = mapOf(":c" to listOf(":b"))
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(edges, droppedIdentityPaths = setOf(":a")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns true when retained task references dropped task`() {
        // `:incrementalReverse mustRunAfter :originalInputs` and `:originalInputs` is dropped.
        val edges = mapOf(":incrementalReverse" to listOf(":originalInputs"))
        assert(SupersetIndexLookup.hasDanglingMustRunAfter(edges, droppedIdentityPaths = setOf(":originalInputs")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false when both edge endpoints are dropped`() {
        // `:b mustRunAfter :a`; both `:a` and `:b` are dropped — no dangle from any retained task.
        val edges = mapOf(":b" to listOf(":a"))
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(edges, droppedIdentityPaths = setOf(":a", ":b")))
    }

    @Test
    fun `hasSideEffectingDroppedTask returns false when no side-effecting tasks recorded`() {
        assert(!SupersetIndexLookup.hasSideEffectingDroppedTask(
            sideEffectingTaskIdentityPaths = emptySet(),
            droppedIdentityPaths = setOf(":b")
        ))
    }

    @Test
    fun `hasSideEffectingDroppedTask returns false on empty dropped set`() {
        // No tasks dropped → no side-effecting task can be dropped.
        assert(!SupersetIndexLookup.hasSideEffectingDroppedTask(
            sideEffectingTaskIdentityPaths = setOf(":cleanSecond"),
            droppedIdentityPaths = emptySet()
        ))
    }

    @Test
    fun `hasSideEffectingDroppedTask returns true when a recorded Delete task is in the dropped set`() {
        // The Bucket2 pattern: `:cleanSecond` is a Delete task scheduled before `:second`.
        // A subset request that drops `:cleanSecond` but retains `:second` would let the
        // loaded cached `:second` output stand without the cleanup; refuse the candidate.
        assert(SupersetIndexLookup.hasSideEffectingDroppedTask(
            sideEffectingTaskIdentityPaths = setOf(":cleanSecond"),
            droppedIdentityPaths = setOf(":cleanSecond")
        ))
    }

    @Test
    fun `hasSideEffectingDroppedTask returns false when the only Delete task is retained`() {
        // `:cleanSecond` recorded as side-effecting; dropped set is `:third` (not a Delete).
        assert(!SupersetIndexLookup.hasSideEffectingDroppedTask(
            sideEffectingTaskIdentityPaths = setOf(":cleanSecond"),
            droppedIdentityPaths = setOf(":third")
        ))
    }

    @Test
    fun `round trip preserves variants including v5 fields`() {
        val tmp = java.io.File.createTempFile("supersetIndex", ".bin").also { it.deleteOnExit() }
        val file = SupersetIndexFile(tmp)
        val variants = listOf(
            IndexedVariant(
                fullKey = "k1",
                cliTokens = listOf("a", "b"),
                entryTaskIdentityPaths = listOf(":a", ":b"),
                mustRunAfterEdges = mapOf(":b" to listOf(":a")),
                dependencyEdges = mapOf(":b" to listOf(":a")),
                sideEffectingTaskIdentityPaths = setOf(":a")
            ),
            // Multi-project bare name: 1 token → 2 identity paths.
            IndexedVariant(
                fullKey = "k2",
                cliTokens = listOf("d"),
                entryTaskIdentityPaths = listOf(":d", ":sub:d"),
                taskGraphAccessed = true
            )
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
