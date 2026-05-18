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


/**
 * One row in the superset index: a stored configuration cache entry's full
 * key, the task list it was stored for, and whether user code observed the
 * task graph during the original build (`true` → exact-match-only at lookup).
 */
internal
data class IndexedVariant(
    val fullKey: String,
    val requestedTasks: List<String>,
    val taskGraphAccessed: Boolean
)


/**
 * Result of a successful superset-index lookup. Carries the chosen entry's
 * full key (used to locate the entry directory) and the task list that entry
 * was originally stored for (so the caller can compute which loaded tasks
 * to prune relative to the current request).
 */
internal
data class CompatibleEntry(
    val fullKey: String,
    val storedRequestedTasks: List<String>
)


/**
 * Pure selection logic for the superset index. No I/O — see `SupersetIndexFile` for persistence.
 */
internal
object SupersetIndex {

    /**
     * Picks the best variant for [requested], or null if no compatible variant exists.
     *
     *  1. Exact match wins (always allowed, even when taskGraphAccessed). The requested
     *     task list, deduplicated, must equal the stored task list — same order included.
     *  2. Otherwise smallest strict superset where the deduplicated request appears as a
     *     subsequence of the stored list (relative order preserved). Variants with
     *     taskGraphAccessed are excluded from this branch.
     *  3. Tie-break for smallest-superset is the caller's responsibility (LRU via
     *     fileAccessTimeJournal); this function returns the first variant of the smallest size.
     */
    fun selectBestMatch(variants: List<IndexedVariant>, requested: List<String>): IndexedVariant? {
        val requestedDistinct = requested.distinct()

        // Step 1: exact match — same task list (duplicates in the request are ignored).
        variants.firstOrNull { it.requestedTasks == requestedDistinct }
            ?.let { return it }

        // Step 2: strict supersets — request appears as a subsequence of a strictly
        // larger stored list. Flagged variants are excluded.
        val eligibleSupersets = variants.filter { v ->
            !v.taskGraphAccessed &&
                v.requestedTasks.size > requestedDistinct.size &&
                isSubsequence(requestedDistinct, v.requestedTasks)
        }
        if (eligibleSupersets.isEmpty()) return null

        return eligibleSupersets.minByOrNull { it.requestedTasks.size }
    }

    /**
     * Returns true if [needle] appears in [haystack] in the same relative order
     * (not necessarily contiguous).
     */
    internal fun isSubsequence(needle: List<String>, haystack: List<String>): Boolean {
        if (needle.isEmpty()) return true
        var i = 0
        for (item in haystack) {
            if (item == needle[i]) {
                i++
                if (i == needle.size) return true
            }
        }
        return false
    }
}


/**
 * Persistence for [IndexedVariant] lists. Binary file format:
 *   magic: 4 bytes ("CCSI" = Configuration Cache Superset Index)
 *   formatVersion: int
 *   count: int
 *   for each variant:
 *     fullKey: UTF
 *     requestedTasks: int count + UTF entries
 *     taskGraphAccessed: boolean
 *
 * Reads from a missing file or one with an unknown format version return an
 * empty list rather than throwing — callers fall back to "no index, cold store".
 */
internal
class SupersetIndexFile(private val file: java.io.File) {

    fun read(): List<IndexedVariant> {
        if (!file.isFile) return emptyList()
        try {
            java.io.DataInputStream(file.inputStream().buffered()).use { input ->
                val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
                if (!magic.contentEquals(MAGIC)) return emptyList()
                val version = input.readInt()
                if (version != FORMAT_VERSION) return emptyList()
                val count = input.readInt()
                return (0 until count).map {
                    val fullKey = input.readUTF()
                    val taskCount = input.readInt()
                    val tasks = (0 until taskCount).map { input.readUTF() }
                    val accessed = input.readBoolean()
                    IndexedVariant(fullKey, tasks, accessed)
                }
            }
        } catch (_: java.io.IOException) {
            // Covers EOFException (subclass) too — partial/corrupt files fall back to empty.
            return emptyList()
        }
    }

    fun write(variants: List<IndexedVariant>) {
        file.parentFile?.let { java.nio.file.Files.createDirectories(it.toPath()) }
        java.io.DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeInt(variants.size)
            for (v in variants) {
                out.writeUTF(v.fullKey)
                out.writeInt(v.requestedTasks.size)
                v.requestedTasks.forEach(out::writeUTF)
                out.writeBoolean(v.taskGraphAccessed)
            }
        }
    }

    companion object {
        private val MAGIC = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), 'I'.code.toByte())
        private const val FORMAT_VERSION = 1
    }
}
