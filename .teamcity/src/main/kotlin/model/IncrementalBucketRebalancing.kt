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

const val DEFAULT_REBALANCE_TOLERANCE = 0.2
const val DEFAULT_MAX_MOVES_PER_COVERAGE = 5

fun rebalanceIncrementally(
    previousBuckets: List<FunctionalTestBucket>,
    subprojectTimes: Map<String, Int>,
    newBucketParallelization: ParallelizationMethod,
    maxSubprojectsPerBucket: Int = MAX_PROJECT_NUMBER_IN_BUCKET,
    tolerance: Double = DEFAULT_REBALANCE_TOLERANCE,
    maxMoves: Int = DEFAULT_MAX_MOVES_PER_COVERAGE,
): List<FunctionalTestBucket> {
    val placed = mutableSetOf<String>()
    val buckets =
        previousBuckets
            .map { bucket ->
                val kept = bucket.subprojects.filter { it in subprojectTimes && placed.add(it) }
                MutableBucket(kept.toMutableList(), bucket.parallelizationMethod, bucket.subprojects.size > 1)
            }.filter { it.subprojects.isNotEmpty() }
            .toMutableList()

    val movable = buckets.filter { it.movable }

    fun MutableBucket.load() = subprojects.sumOf { subprojectTimes.getValue(it) }

    subprojectTimes.keys.filter { it !in placed }.forEach { newSubproject ->
        val target = movable.filter { it.subprojects.size < maxSubprojectsPerBucket }.minByOrNull { it.load() }
        if (target != null) {
            target.subprojects.add(newSubproject)
        } else {
            buckets.add(MutableBucket(mutableListOf(newSubproject), newBucketParallelization, false))
        }
    }

    if (movable.size > 1) {
        val limit = movable.sumOf { it.load() }.toDouble() / movable.size * (1 + tolerance)
        var moves = 0
        while (moves < maxMoves) {
            val heaviest = movable.maxBy { it.load() }
            val heaviestLoad = heaviest.load()
            if (heaviestLoad <= limit || heaviest.subprojects.size < 2) {
                break
            }
            val lightest =
                movable.filter { it !== heaviest && it.subprojects.size < maxSubprojectsPerBucket }.minByOrNull { it.load() } ?: break
            val lightestLoad = lightest.load()
            val best =
                heaviest.subprojects
                    .map { it to maxOf(heaviestLoad - subprojectTimes.getValue(it), lightestLoad + subprojectTimes.getValue(it)) }
                    .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            if (best == null || best.second >= heaviestLoad) {
                break
            }
            heaviest.subprojects.remove(best.first)
            lightest.subprojects.add(best.first)
            moves++
        }
    }

    return buckets.map { FunctionalTestBucket(it.subprojects, it.parallelizationMethod) }
}

private class MutableBucket(
    val subprojects: MutableList<String>,
    val parallelizationMethod: ParallelizationMethod,
    val movable: Boolean,
)
