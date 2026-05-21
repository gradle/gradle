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

import org.gradle.api.logging.Logging


/**
 * Pure selection logic for the superset index. No I/O — see [SupersetIndexFile] for persistence.
 */
internal
object SupersetIndexLookup {

    /**
     * One row in the superset index. Fields:
     *
     *  - [fullKey]: the stored configuration cache entry's full key (also its directory name).
     *  - [cliTokens]: the user's CLI request verbatim (`"a"`, `":foo"`, `":proj:bar"`, etc.).
     *    Used as the match key against the current build's request — verbatim string comparison
     *    so bare names and absolute paths cannot collide (`d` does not match `:d`).
     *  - [entryTaskIdentityPaths]: identity paths of the entry tasks the original build scheduled.
     *    Used to compute which loaded tasks to prune when reusing this entry for a subset request.
     *    When `cliTokens.size == entryTaskIdentityPaths.size`, the two lists are positionally
     *    paired (CLI token at index `i` resolved to the identity path at index `i`) and the
     *    mapping supports safe subset pruning. When the sizes differ — a multi-project build
     *    where one bare CLI token resolved to multiple identity paths across subprojects — the
     *    mapping is ambiguous and the entry is treated as exact-match-only (see [selectBestMatch]).
     *  - [taskGraphAccessed]: whether user code observed the task graph during the original build
     *    (`true` → exact-match-only at lookup; see [selectBestMatch]).
     *  - [mustRunAfterEdges]: `mustRunAfter` / finalizer edges between scheduled tasks (source
     *    identity path → target identity paths). Used to reject a candidate at lookup time when
     *    pruning would leave a retained task referencing a dropped task through a non-dependency
     *    hard-ordering edge — the loaded plan would either deadlock or silently execute a
     *    non-requested task. See [hasDanglingMustRunAfter].
     *  - [sideEffectingTaskIdentityPaths]: identity paths of scheduled tasks whose execution
     *    has filesystem side effects beyond the snapshotted-output set — specifically, tasks
     *    declaring a property annotated `@org.gradle.api.tasks.Destroys`. `Delete` is caught
     *    via its `@Destroys`-annotated `getTargetFiles()`; user-defined task types with
     *    `@Destroys` properties are caught the same way. Used to reject a candidate when
     *    subset pruning would drop one of these tasks: skipping the deletion but loading the
     *    cached pre-deletion output state breaks the filesystem invariant retained tasks rely
     *    on. See [hasSideEffectingDroppedTask].
     *    A set rather than a per-task path map because detection uses `TypeMetadataStore`
     *    annotation lookup only — no property getter is invoked, so the check is free of the
     *    per-task footprint walk that would re-invoke user `@OutputFile` / `@Nested` getters.
     */
    data class IndexedVariant(
        val fullKey: String,
        val cliTokens: List<String>,
        val entryTaskIdentityPaths: List<String>,
        val taskGraphAccessed: Boolean = false,
        val mustRunAfterEdges: Map<String, List<String>> = emptyMap(),
        val sideEffectingTaskIdentityPaths: Set<String> = emptySet()
    ) {
        /**
         * `true` when the CLI tokens and entry identity paths are positionally paired
         * (same length). Required for safe subset pruning.
         */
        val hasOneToOneCliMapping: Boolean
            get() = cliTokens.size == entryTaskIdentityPaths.size
    }

    /**
     * Result of a successful superset-index lookup. Carries the chosen entry's
     * full key (used to locate the entry directory), the CLI tokens it was stored
     * for, and the resolved entry-task identity paths. The caller derives the
     * dropped-identity-path set by mapping current CLI tokens through the
     * `cliTokens → entryTaskIdentityPaths` positional pairing.
     */
    data class CompatibleEntry(
        val fullKey: String,
        val storedCliTokens: List<String>,
        val storedEntryTaskIdentityPaths: List<String>
    )

    /**
     * Picks the best variant for [requested], or null if no compatible variant exists.
     * Matching operates on [IndexedVariant.cliTokens] — verbatim string comparison so
     * bare and absolute task names don't collide.
     *
     *  1. Exact match wins (always allowed, even when taskGraphAccessed or no 1:1 mapping):
     *     the deduplicated request equals the stored CLI list — same order included.
     *  2. Otherwise smallest strict superset where the deduplicated request appears as a
     *     subsequence of the stored CLI list (relative order preserved). Variants are
     *     excluded from this branch if `taskGraphAccessed` is true or
     *     [IndexedVariant.hasOneToOneCliMapping] is false (multi-project bare-name entries
     *     can't safely derive the dropped identity-path set from the stored mapping).
     *  3. Tie-break for smallest-superset is the caller's responsibility (LRU via
     *     fileAccessTimeJournal); this function returns the first variant of the smallest size.
     */
    fun selectBestMatch(variants: List<IndexedVariant>, requested: List<String>): IndexedVariant? {
        val requestedDistinct = requested.distinct()

        // Step 1: exact match — same CLI list (duplicates in the request are ignored).
        variants.firstOrNull { it.cliTokens == requestedDistinct }
            ?.let { return it }

        // Step 2: strict supersets — request appears as a subsequence of a strictly
        // larger stored list. Flagged/non-1:1 variants are excluded.
        val eligibleSupersets = variants.filter { v ->
            !v.taskGraphAccessed &&
                v.hasOneToOneCliMapping &&
                v.cliTokens.size > requestedDistinct.size &&
                isSubsequence(requestedDistinct, v.cliTokens)
        }
        if (eligibleSupersets.isEmpty()) return null

        // Step 3: select the smallest strict superset
        return eligibleSupersets.minByOrNull { it.cliTokens.size }
    }

    /**
     * Returns true if pruning the loaded plan would leave a retained task pointing
     * at a dropped task through a mustRunAfter / finalizer edge. The loaded plan
     * can't honor such an edge safely — the dropped task isn't in the plan
     * (deadlock) or our BFS retention pulls it back in and it runs (different
     * semantics than a fresh subset request).
     *
     * Returns false when [droppedIdentityPaths] is empty (exact match — no pruning)
     * or the dropped set is disjoint from every edge's targets.
     */
    fun hasDanglingMustRunAfter(
        edges: Map<String, List<String>>,
        droppedIdentityPaths: Set<String>
    ): Boolean {
        if (edges.isEmpty() || droppedIdentityPaths.isEmpty()) return false
        return edges.any { (source, targets) ->
            source !in droppedIdentityPaths && targets.any { it in droppedIdentityPaths }
        }
    }

    /**
     * Returns true if any of the to-be-dropped tasks has side effects beyond its
     * snapshotted outputs — specifically, tasks declaring a property annotated
     * `@org.gradle.api.tasks.Destroys`. `Delete` is the canonical case
     * (`Delete.getTargetFiles()` is `@Destroys`), and so are user-defined task
     * types with `@Destroys` properties. Skipping such a task while loading the
     * cached pre-deletion output state leaves filesystem invariants the retained
     * tasks rely on broken — e.g. a `clean*` task that would have cleared a
     * directory before another task wrote into it.
     * <p>
     * This is the practical heuristic that replaces a more precise pairwise output-path
     * overlap check: capturing per-task output paths through the property walker would
     * re-invoke user `@OutputFile` / `@Nested` getters at store time, which is
     * observable as an extra invocation count in tests like
     * `TaskParametersIntegrationTest."input and output properties are not evaluated too often"`.
     * Detection uses `TypeMetadataStore` annotation lookup only — no property getter
     * is invoked, so the check is free of that side effect while still catching the
     * cleanX overlap scenarios that motivated the gate.
     * <p>
     * Caveat: dynamic destroyable registration via `task.destroyables.register(...)`
     * on a plain `DefaultTask` is not detected — annotation lookup can't see runtime
     * registrations.
     * <p>
     * Returns `false` when [droppedIdentityPaths] is empty (exact match — no pruning)
     * or the candidate has no recorded side-effecting tasks.
     */
    fun hasSideEffectingDroppedTask(
        sideEffectingTaskIdentityPaths: Set<String>,
        droppedIdentityPaths: Set<String>
    ): Boolean {
        if (sideEffectingTaskIdentityPaths.isEmpty() || droppedIdentityPaths.isEmpty()) return false
        return droppedIdentityPaths.any { it in sideEffectingTaskIdentityPaths }
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
 * Persistence for [SupersetIndexLookup.IndexedVariant] lists. Binary file format:
 *   magic: 4 bytes ("CCSI" = Configuration Cache Superset Index)
 *   formatVersion: int
 *   count: int
 *   for each variant:
 *     fullKey: UTF
 *     cliTokens: int count + UTF entries
 *     entryTaskIdentityPaths: int count + UTF entries
 *     taskGraphAccessed: boolean
 *     mustRunAfterEdges: edge-map (see writeEdgeMap)
 *     sideEffectingTaskIdentityPaths: int count + UTF entries
 *
 * An edge-map is encoded as: int entry-count, each entry = UTF source + int target-count + UTF targets.
 * <p>
 * Reads from a missing file or one with an unknown format version return an
 * empty list rather than throwing — callers fall back to "no index, cold store".
 */
internal
class SupersetIndexFile(private val file: java.io.File) {

    fun read(): List<SupersetIndexLookup.IndexedVariant> {
        if (!file.isFile) return emptyList()
        try {
            java.io.DataInputStream(file.inputStream().buffered()).use { input ->
                val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
                if (!magic.contentEquals(MAGIC)) {
                    logger.debug("Discarding superset index at {} — magic header mismatch (file written by an incompatible Gradle build or non-index data).", file)
                    return emptyList()
                }
                val version = input.readInt()
                if (version != FORMAT_VERSION) {
                    logger.debug("Discarding superset index at {} — format version {} does not match expected {}; falling back to cold-store.", file, version, FORMAT_VERSION)
                    return emptyList()
                }
                val count = input.readInt()
                return (0 until count).map {
                    val fullKey = input.readUTF()
                    val cliTokens = readStringList(input)
                    val entryTaskIdentityPaths = readStringList(input)
                    val accessed = input.readBoolean()
                    val mustRunAfterEdges = readEdgeMap(input)
                    val sideEffecting = readStringList(input).toSet()
                    SupersetIndexLookup.IndexedVariant(
                        fullKey, cliTokens, entryTaskIdentityPaths, accessed,
                        mustRunAfterEdges, sideEffecting
                    )
                }
            }
        } catch (e: java.io.IOException) {
            // Covers EOFException (subclass) too — partial/corrupt files fall back to empty.
            logger.debug("Discarding superset index at {} — IO error while reading ({}); falling back to cold-store.", file, e.toString())
            return emptyList()
        }
    }

    fun write(variants: List<SupersetIndexLookup.IndexedVariant>) {
        file.parentFile?.let { java.nio.file.Files.createDirectories(it.toPath()) }
        java.io.DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeInt(variants.size)
            for (v in variants) {
                out.writeUTF(v.fullKey)
                writeStringList(out, v.cliTokens)
                writeStringList(out, v.entryTaskIdentityPaths)
                out.writeBoolean(v.taskGraphAccessed)
                writeEdgeMap(out, v.mustRunAfterEdges)
                writeStringList(out, v.sideEffectingTaskIdentityPaths.toList())
            }
        }
    }

    private
    fun readStringList(input: java.io.DataInput): List<String> {
        val n = input.readInt()
        return (0 until n).map { input.readUTF() }
    }

    private
    fun writeStringList(out: java.io.DataOutput, list: List<String>) {
        out.writeInt(list.size)
        list.forEach(out::writeUTF)
    }

    private
    fun readEdgeMap(input: java.io.DataInput): Map<String, List<String>> {
        val entryCount = input.readInt()
        return (0 until entryCount).associate {
            val source = input.readUTF()
            val targetCount = input.readInt()
            val targets = (0 until targetCount).map { input.readUTF() }
            source to targets
        }
    }

    private
    fun writeEdgeMap(out: java.io.DataOutput, map: Map<String, List<String>>) {
        out.writeInt(map.size)
        for ((source, targets) in map) {
            out.writeUTF(source)
            out.writeInt(targets.size)
            targets.forEach(out::writeUTF)
        }
    }

    companion object {
        private val logger = Logging.getLogger(SupersetIndexFile::class.java)
        private val MAGIC = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), 'I'.code.toByte())
        // v1 = (fullKey, requestedTasks, taskGraphAccessed)
        // v2 = v1 + mustRunAfterEdges
        // v3 = v2 + dependencyEdges + outputPaths
        // v4 = v3 with requestedTasks split into separate cliTokens and entryTaskIdentityPaths
        // v5 = v4 with outputPaths replaced by sideEffectingTaskIdentityPaths (@Destroys-property gate)
        // v6 = v5 with dependencyEdges removed (never consulted by any safety gate)
        private const val FORMAT_VERSION = 6
    }
}
