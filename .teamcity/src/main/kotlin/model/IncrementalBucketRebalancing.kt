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

package model

import configurations.ParallelizationMethod
import kotlin.math.ceil
import kotlin.math.roundToInt

const val DEFAULT_REBALANCE_TOLERANCE = 0.2
const val DEFAULT_MAX_MOVES_PER_COVERAGE = 5
const val OVERSIZED_BUCKET_LOAD_FACTOR = 2.0
const val LARGE_SUBPROJECT_KEEP_FACTOR = 0.5

private fun splitByLoad(
    subprojects: List<String>,
    parts: Int,
    maxPerPart: Int,
    time: (String) -> Int,
): List<List<String>> {
    val bins = List(parts) { mutableListOf<String>() }
    val loads = IntArray(parts)
    subprojects.sortedWith(compareByDescending<String> { time(it) }.thenBy { it }).forEach { subproject ->
        val target = bins.indices.filter { bins[it].size < maxPerPart }.minBy { loads[it] }
        bins[target].add(subproject)
        loads[target] += time(subproject)
    }
    return bins.map { bin -> bin.sortedBy { subprojects.indexOf(it) } }
}

fun rebalanceIncrementally(
    previousBuckets: List<Collection<String>>,
    subprojectTimes: Map<String, Int>,
    expectedBucketNumber: Int,
    parallelization: (Int) -> ParallelizationMethod,
    maxSubprojectsPerBucket: Int = MAX_PROJECT_NUMBER_IN_BUCKET,
    tolerance: Double = DEFAULT_REBALANCE_TOLERANCE,
    maxMoves: Int = DEFAULT_MAX_MOVES_PER_COVERAGE,
): List<FunctionalTestBucket> {
    fun time(subproject: String) = subprojectTimes.getValue(subproject)

    fun MutableBucket.load() = subprojects.sumOf { time(it) }

    val byTimeDescending = compareByDescending<String> { time(it) }.thenBy { it }
    val targetBucketLoad = subprojectTimes.values.sum().toDouble() / expectedBucketNumber

    fun isLarge(
        subproject: String,
        wasAlone: Boolean,
    ) = targetBucketLoad > 0 && time(subproject) >= targetBucketLoad * (if (wasAlone) LARGE_SUBPROJECT_KEEP_FACTOR else 1.0)

    val placed = mutableSetOf<String>()
    val buckets = mutableListOf<MutableBucket>()
    val extractedLarge = mutableListOf<String>()
    val ungrouped = mutableListOf<String>()
    previousBuckets.forEach { previous ->
        val kept = previous.filter { it in subprojectTimes && placed.add(it) }
        val wasAlone = previous.size == 1
        val (large, small) = kept.partition { isLarge(it, wasAlone) }
        when {
            wasAlone && large.isNotEmpty() -> {
                buckets.add(MutableBucket(large.toMutableList(), false))
            }

            small.size > 1 -> {
                buckets.add(MutableBucket(small.toMutableList(), true))
            }

            else -> {
                buckets.add(MutableBucket(mutableListOf(), true))
                ungrouped.addAll(small)
            }
        }
        if (!wasAlone) {
            extractedLarge.addAll(large)
        }
    }
    val (newLarge, newSmall) = subprojectTimes.keys.filter { it !in placed }.partition { isLarge(it, false) }
    (extractedLarge + newLarge).sortedWith(byTimeDescending).forEach { buckets.add(MutableBucket(mutableListOf(it), false)) }
    ungrouped.addAll(newSmall)

    val movable = buckets.filter { it.movable && it.subprojects.isNotEmpty() }.toMutableList()

    fun addMovable(subprojects: List<String>) {
        val bucket = MutableBucket(subprojects.toMutableList(), true)
        buckets.add(bucket)
        movable.add(bucket)
    }

    fun lightestWithRoom(except: MutableBucket? = null) =
        movable.filter { it !== except && it.subprojects.size < maxSubprojectsPerBucket }.minByOrNull { it.load() }

    ungrouped.sortedWith(byTimeDescending).forEach { subproject ->
        val target = lightestWithRoom()
        if (target != null) {
            target.subprojects.add(subproject)
        } else {
            addMovable(listOf(subproject))
        }
    }

    movable.toList().forEach { oversized ->
        if (targetBucketLoad > 0 && oversized.load() > targetBucketLoad * OVERSIZED_BUCKET_LOAD_FACTOR) {
            val partCount =
                maxOf(
                    ceil(oversized.load() / targetBucketLoad).toInt(),
                    ceil(oversized.subprojects.size.toDouble() / maxSubprojectsPerBucket).toInt(),
                ).coerceAtMost(oversized.subprojects.size / 2)
            if (partCount > 1) {
                val parts = splitByLoad(oversized.subprojects, partCount, maxSubprojectsPerBucket) { time(it) }
                oversized.subprojects.clear()
                oversized.subprojects.addAll(parts.first())
                parts.drop(1).forEach { addMovable(it) }
            }
        }
    }

    fun bestMove(donor: MutableBucket): Triple<String, MutableBucket, MutableBucket>? {
        val receiver = lightestWithRoom(donor) ?: return null
        val donorLoad = donor.load()
        val receiverLoad = receiver.load()
        return donor.subprojects
            .map { it to maxOf(donorLoad - time(it), receiverLoad + time(it)) }
            .filter { it.second < donorLoad }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            ?.let { Triple(it.first, donor, receiver) }
    }

    if (movable.size > 1) {
        val limit = movable.sumOf { it.load() }.toDouble() / movable.size * (1 + tolerance)
        var moves = 0
        while (moves < maxMoves) {
            val (subproject, donor, receiver) =
                movable
                    .filter { it.subprojects.size > 2 && it.load() > limit }
                    .sortedByDescending { it.load() }
                    .firstNotNullOfOrNull { bestMove(it) }
                    ?: break
            donor.subprojects.remove(subproject)
            receiver.subprojects.add(subproject)
            moves++
        }
    }

    while (true) {
        val hole = buckets.indexOfFirst { it.subprojects.isEmpty() }
        if (hole < 0) {
            break
        }
        val last = buckets.removeAt(buckets.lastIndex)
        if (hole < buckets.size) {
            buckets[hole] = last
        }
    }

    return buckets.map { bucket ->
        if (bucket.movable) {
            FunctionalTestBucket(bucket.subprojects, parallelization(1))
        } else {
            val batches = (bucket.load() / targetBucketLoad).roundToInt().coerceAtLeast(1)
            FunctionalTestBucket(bucket.subprojects, parallelization(batches))
        }
    }
}

private class MutableBucket(
    val subprojects: MutableList<String>,
    val movable: Boolean,
)
