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
    fun `hasDanglingMustRunAfter returns false when no edges`() {
        // No edges at all: pruning is safe regardless of subset shape.
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(emptyMap(), listOf(":a", ":b"), listOf(":a")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false on exact match`() {
        // No tasks dropped: edges can't dangle.
        val edges = mapOf(":b" to listOf(":a"))
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(edges, listOf(":a", ":b"), listOf(":a", ":b")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false when edge stays within retained set`() {
        // Stored has :a, :b, :c with `:c mustRunAfter :b`. Request is :b, :c — :a dropped.
        // The edge's source and target are both retained.
        val edges = mapOf(":c" to listOf(":b"))
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(edges, listOf(":a", ":b", ":c"), listOf(":b", ":c")))
    }

    @Test
    fun `hasDanglingMustRunAfter returns true when retained task references dropped task`() {
        // The Bucket4 pattern: stored has :originalInputs, :incrementalReverse with
        // `:incrementalReverse mustRunAfter :originalInputs`. Request is just
        // :incrementalReverse — :originalInputs would be dropped. Edge dangles.
        val edges = mapOf(":incrementalReverse" to listOf(":originalInputs"))
        assert(SupersetIndexLookup.hasDanglingMustRunAfter(
            edges,
            listOf(":originalInputs", ":incrementalReverse"),
            listOf(":incrementalReverse")
        ))
    }

    @Test
    fun `hasDanglingMustRunAfter returns false when both edge endpoints are dropped`() {
        // Stored has :a, :b, :c, :d with `:b mustRunAfter :a`. Request is :c, :d.
        // Both source and target are dropped — no dangle from a *retained* task.
        val edges = mapOf(":b" to listOf(":a"))
        assert(!SupersetIndexLookup.hasDanglingMustRunAfter(edges, listOf(":a", ":b", ":c", ":d"), listOf(":c", ":d")))
    }

    @Test
    fun `hasOverlappingDroppedOutputs returns false when no output paths recorded`() {
        // No outputPaths data (e.g. pre-v3 index, or all tasks had unresolvable outputs):
        // overlap check can't fire.
        assert(!SupersetIndexLookup.hasOverlappingDroppedOutputs(
            outputPaths = emptyMap(),
            dependencyEdges = emptyMap(),
            stored = listOf(":a", ":b"),
            requested = listOf(":a")
        ))
    }

    @Test
    fun `hasOverlappingDroppedOutputs returns false on exact match`() {
        // No pruning happens — overlap is irrelevant.
        val outputs = mapOf(":a" to listOf("/out/a"), ":b" to listOf("/out/b"))
        assert(!SupersetIndexLookup.hasOverlappingDroppedOutputs(
            outputPaths = outputs,
            dependencyEdges = emptyMap(),
            stored = listOf(":a", ":b"),
            requested = listOf(":a", ":b")
        ))
    }

    @Test
    fun `hasOverlappingDroppedOutputs returns true when dropped output equals retained output`() {
        // The Bucket2 pattern: `:cleanSecond` writes to `/out/second.txt` (clearing it),
        // `:second` writes to the same path. Dropping `:cleanSecond` while retaining
        // `:second` would let the loaded cached `:second` output stand without the
        // cleanup invariant the original build relied on.
        val outputs = mapOf(
            ":first" to listOf("/build/first.txt"),
            ":cleanSecond" to listOf("/build/second.txt"),
            ":second" to listOf("/build/second.txt")
        )
        assert(SupersetIndexLookup.hasOverlappingDroppedOutputs(
            outputPaths = outputs,
            dependencyEdges = emptyMap(),
            stored = listOf(":first", ":cleanSecond", ":second"),
            requested = listOf(":first", ":second")
        ))
    }

    @Test
    fun `hasOverlappingDroppedOutputs returns true when retained output is inside dropped output dir`() {
        // Dropped `:cleanDir` writes a directory; retained `:writeFile` writes a file inside.
        val outputs = mapOf(
            ":cleanDir" to listOf("/build/out"),
            ":writeFile" to listOf("/build/out/file.txt")
        )
        assert(SupersetIndexLookup.hasOverlappingDroppedOutputs(
            outputPaths = outputs,
            dependencyEdges = emptyMap(),
            stored = listOf(":cleanDir", ":writeFile"),
            requested = listOf(":writeFile")
        ))
    }

    @Test
    fun `hasOverlappingDroppedOutputs returns false when outputs are sibling-unrelated`() {
        // `/build/out` and `/build/outdoor` share a literal prefix but no path-segment
        // ancestry — they're siblings, not parent/child.
        val outputs = mapOf(
            ":a" to listOf("/build/out"),
            ":b" to listOf("/build/outdoor")
        )
        assert(!SupersetIndexLookup.hasOverlappingDroppedOutputs(
            outputPaths = outputs,
            dependencyEdges = emptyMap(),
            stored = listOf(":a", ":b"),
            requested = listOf(":b")
        ))
    }

    @Test
    fun `hasOverlappingDroppedOutputs retains transitive deps via BFS so their outputs are not flagged dropped`() {
        // `:entry` depends on `:dep`. Stored = [:entry], plus an entry `:other` that
        // gets dropped from the requested set. `:dep`'s output (recorded under its own
        // identity path) is retained by BFS through dependencyEdges and must NOT be
        // considered as part of the dropped set when comparing against `:other`.
        val outputs = mapOf(
            ":entry" to listOf("/build/entry.txt"),
            ":dep" to listOf("/build/dep.txt"),
            ":other" to listOf("/build/other.txt")
        )
        val deps = mapOf(":entry" to listOf(":dep"))
        // Request keeps `:entry`, drops `:other`. `:dep` survives via dependency BFS.
        assert(!SupersetIndexLookup.hasOverlappingDroppedOutputs(
            outputPaths = outputs,
            dependencyEdges = deps,
            stored = listOf(":entry", ":other"),
            requested = listOf(":entry")
        ))
    }

    @Test
    fun `round trip preserves variants including v3 fields`() {
        val tmp = java.io.File.createTempFile("supersetIndex", ".bin").also { it.deleteOnExit() }
        val file = SupersetIndexFile(tmp)
        val variants = listOf(
            IndexedVariant(
                fullKey = "k1",
                requestedTasks = listOf(":a", ":b"),
                mustRunAfterEdges = mapOf(":b" to listOf(":a")),
                dependencyEdges = mapOf(":b" to listOf(":a")),
                outputPaths = mapOf(":a" to listOf("/out/a"), ":b" to listOf("/out/b"))
            ),
            IndexedVariant("k2", listOf(":a"), taskGraphAccessed = true)
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
