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
 * Pure selection logic for the superset index. No I/O — see [SupersetIndexFile] for persistence.
 */
internal
object SupersetIndexLookup {

    /**
     * One row in the superset index. Fields:
     *
     *  - [fullKey]: the stored configuration cache entry's full key (also its directory name).
     *  - [requestedTasks]: identity paths of the entry tasks the build was originally requested for.
     *  - [taskGraphAccessed]: whether user code observed the task graph during the original build
     *    (`true` → exact-match-only at lookup; see [selectBestMatch] step 2).
     *  - [mustRunAfterEdges]: `mustRunAfter` / finalizer edges between scheduled tasks (source
     *    identity path → target identity paths). Used to reject a candidate at lookup time when
     *    pruning would leave a retained task referencing a dropped task through a non-dependency
     *    hard-ordering edge — the loaded plan would either deadlock or silently execute a
     *    non-requested task. See [hasDanglingMustRunAfter].
     *  - [dependencyEdges]: `dependencySuccessors` edges between scheduled tasks (source identity
     *    path → identity paths the source depends on). Needed to compute the retained closure for
     *    the overlap check below.
     *  - [outputPaths]: declared output file paths for each scheduled task (identity path →
     *    absolute paths). Used to reject a candidate when pruning would drop a task whose declared
     *    outputs overlap with a retained task's outputs — the loaded plan would skip filesystem
     *    side effects (e.g. a `clean*` task) that the retained tasks rely on observing. See
     *    [hasOverlappingDroppedOutputs].
     */
    data class IndexedVariant(
        val fullKey: String,
        val requestedTasks: List<String>,
        val taskGraphAccessed: Boolean = false,
        val mustRunAfterEdges: Map<String, List<String>> = emptyMap(),
        val dependencyEdges: Map<String, List<String>> = emptyMap(),
        val outputPaths: Map<String, List<String>> = emptyMap()
    )

    /**
     * Result of a successful superset-index lookup. Carries the chosen entry's
     * full key (used to locate the entry directory) and the task list that entry
     * was originally stored for (so the caller can compute which loaded tasks
     * to prune relative to the current request).
     */
    data class CompatibleEntry(
        val fullKey: String,
        val storedRequestedTasks: List<String>
    )

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
     * Returns true if pruning [stored] down to [requested] would leave a retained
     * task pointing at a dropped task through a mustRunAfter / finalizer edge.
     * The loaded plan can't honor such an edge safely — the dropped task isn't in
     * the plan (deadlock) or our BFS retention pulls it back in and it runs
     * (different semantics than a fresh subset request).
     *
     * Returns false when no pruning happens ([stored] == [requested] dedup'd) or
     * when the dropped set is disjoint from every edge's targets.
     */
    fun hasDanglingMustRunAfter(
        edges: Map<String, List<String>>,
        stored: List<String>,
        requested: List<String>
    ): Boolean {
        if (edges.isEmpty()) return false
        val tasksToDrop = stored.toSet() - requested.toSet()
        if (tasksToDrop.isEmpty()) return false
        return edges.any { (source, targets) ->
            source !in tasksToDrop && targets.any { it in tasksToDrop }
        }
    }

    /**
     * Returns true if pruning [stored] down to [requested] would leave a dropped
     * task whose declared outputs overlap with a retained task's declared outputs.
     * <p>
     * Dropped tasks may have produced filesystem side effects the retained tasks
     * observe at execution time — e.g. a `clean*` task scheduled in the original
     * build cleared a directory before another task wrote into it. Skipping the
     * cleanup but loading the cached pre-cleanup output state changes the
     * effective filesystem invariant for the retained tasks (`OverlappingOutputs`
     * detection notices the leftover content and re-executes the retained task —
     * a behavior the loaded plan can't reproduce).
     * <p>
     * Retained set is computed by BFS from `requested ∩ stored` (entries that
     * survive the request shrink) traversing [dependencyEdges] forward; anything
     * not in the closure but listed in [outputPaths] is dropped. Returns `false`
     * on exact match (no pruning) and when neither side has any output paths.
     */
    fun hasOverlappingDroppedOutputs(
        outputPaths: Map<String, List<String>>,
        dependencyEdges: Map<String, List<String>>,
        stored: List<String>,
        requested: List<String>
    ): Boolean {
        val requestedSet = requested.toSet()
        val storedSet = stored.toSet()
        if (outputPaths.isEmpty() || storedSet == requestedSet) return false
        val retained = retainedClosure(requestedSet intersect storedSet, dependencyEdges)
        val droppedPaths = (outputPaths.keys - retained).flatMap { outputPaths[it].orEmpty() }
        val retainedPaths = retained.flatMap { outputPaths[it].orEmpty() }
        // Pairwise prefix-overlap check. Small in practice — declared outputs per task
        // are usually 1–2 paths, and only mismatched (dropped vs retained) pairs count.
        return droppedPaths.any { d -> retainedPaths.any { r -> pathOverlaps(d, r) } }
    }

    private
    fun retainedClosure(
        seeds: Set<String>,
        dependencyEdges: Map<String, List<String>>
    ): Set<String> {
        val visited = HashSet<String>(seeds)
        val queue = ArrayDeque(seeds)
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            for (dep in dependencyEdges[next].orEmpty()) {
                if (visited.add(dep)) queue.addLast(dep)
            }
        }
        return visited
    }

    /**
     * True when [a] and [b] are equal, or one is a sub-path of the other under
     * filesystem-style segment semantics. `/a/b` overlaps `/a/b/c.txt` but not
     * `/a/bridge.txt`. Comparison is verbatim on the stored absolute path strings;
     * paths captured at store time share the same OS and case-sensitivity as the
     * paths captured for the current build.
     */
    private
    fun pathOverlaps(a: String, b: String): Boolean {
        if (a == b) return true
        val sep = java.io.File.separator
        val aWithSep = if (a.endsWith(sep)) a else a + sep
        val bWithSep = if (b.endsWith(sep)) b else b + sep
        return aWithSep.startsWith(bWithSep) || bWithSep.startsWith(aWithSep)
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
 *     requestedTasks: int count + UTF entries
 *     taskGraphAccessed: boolean
 *     mustRunAfterEdges: edge-map (see writeEdgeMap)
 *     dependencyEdges: edge-map
 *     outputPaths: edge-map
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
                if (!magic.contentEquals(MAGIC)) return emptyList()
                val version = input.readInt()
                if (version != FORMAT_VERSION) return emptyList()
                val count = input.readInt()
                return (0 until count).map {
                    val fullKey = input.readUTF()
                    val taskCount = input.readInt()
                    val tasks = (0 until taskCount).map { input.readUTF() }
                    val accessed = input.readBoolean()
                    val mustRunAfterEdges = readEdgeMap(input)
                    val dependencyEdges = readEdgeMap(input)
                    val outputPaths = readEdgeMap(input)
                    SupersetIndexLookup.IndexedVariant(
                        fullKey, tasks, accessed, mustRunAfterEdges, dependencyEdges, outputPaths
                    )
                }
            }
        } catch (_: java.io.IOException) {
            // Covers EOFException (subclass) too — partial/corrupt files fall back to empty.
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
                out.writeInt(v.requestedTasks.size)
                v.requestedTasks.forEach(out::writeUTF)
                out.writeBoolean(v.taskGraphAccessed)
                writeEdgeMap(out, v.mustRunAfterEdges)
                writeEdgeMap(out, v.dependencyEdges)
                writeEdgeMap(out, v.outputPaths)
            }
        }
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
        private val MAGIC = byteArrayOf('C'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), 'I'.code.toByte())
        // v1 = (fullKey, requestedTasks, taskGraphAccessed)
        // v2 = v1 + mustRunAfterEdges
        // v3 = v2 + dependencyEdges + outputPaths
        private const val FORMAT_VERSION = 3
    }
}
