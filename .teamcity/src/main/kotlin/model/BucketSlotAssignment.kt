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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * The position of a bucket within a test coverage is part of the id of the TeamCity build configuration
 * it produces, see [getBucketUuid]. Moving a bucket to a different position therefore hands its build
 * configuration a different set of subprojects, which invalidates that configuration's caches and history.
 *
 * This holds the bucket layout of a previously generated `test-buckets.json` so a newly computed split can
 * be emitted in the same order, see [assignToPreviousSlots].
 *
 * The file is not purely machine generated - it is regularly edited by hand to rename subprojects, drop
 * deleted ones or re-split a single coverage - so it is read leniently: entries that no longer make sense
 * are simply not matched against, they never fail the generation.
 */
class PreviousBucketLayout(
    private val slotsByCoverageUuid: Map<Int, List<Set<String>>>,
) {
    /**
     * The subprojects of each bucket of the given test coverage, in slot order.
     * Empty when the coverage did not appear in the previous layout, which means "no constraint".
     */
    fun slotsFor(testCoverageUuid: Int): List<Set<String>> = slotsByCoverageUuid[testCoverageUuid] ?: emptyList()

    companion object {
        val EMPTY = PreviousBucketLayout(emptyMap())

        fun readFrom(jsonFile: File): PreviousBucketLayout {
            if (!jsonFile.isFile) {
                return EMPTY
            }
            return try {
                val coverages: List<Map<String, Any>> = ObjectMapper().registerKotlinModule().readValue(jsonFile.readText())
                val slotsByCoverageUuid = mutableMapOf<Int, List<Set<String>>>()
                coverages.forEach { coverage ->
                    val uuid = coverage["testCoverageUuid"]?.toString()?.toIntOrNull()
                    val buckets = coverage["buckets"] as? List<*>
                    if (uuid != null && buckets != null) {
                        slotsByCoverageUuid[uuid] =
                            buckets.map { bucket ->
                                ((bucket as? Map<*, *>)?.get("subprojects") as? List<*>)
                                    ?.map { it.toString() }
                                    ?.toSet()
                                    ?: emptySet()
                            }
                    }
                }
                PreviousBucketLayout(slotsByCoverageUuid)
            } catch (e: Exception) {
                println("Ignoring unreadable previous bucket layout in ${jsonFile.absolutePath}: $e")
                EMPTY
            }
        }
    }
}

/**
 * Reorders freshly computed buckets so that each one keeps the slot its closest predecessor occupied in
 * [previousSlots], which keeps the generated `test-buckets.json` - and with it the TeamCity build
 * configurations derived from it - as close to unchanged as the new split allows.
 *
 * Buckets are matched to slots by how much their subprojects overlap, most similar pair first, so an
 * unchanged bucket always reclaims its own slot. Buckets without a predecessor fill the remaining slots in
 * order; if the new split has more buckets than the previous one the surplus is appended.
 *
 * This only changes the order of [newBuckets], never their contents, and is a no-op when [previousSlots] is
 * empty.
 */
fun <T> assignToPreviousSlots(
    newBuckets: List<T>,
    previousSlots: List<Set<String>>,
    subprojectsOf: (T) -> Set<String>,
): List<T> {
    if (previousSlots.isEmpty() || newBuckets.isEmpty()) {
        return newBuckets
    }

    val newSubprojects = newBuckets.map(subprojectsOf)
    val candidates =
        newSubprojects
            .flatMapIndexed { newIndex: Int, subprojects: Set<String> ->
                previousSlots.mapIndexedNotNull { slotIndex, previousSubprojects ->
                    val similarity = similarity(subprojects, previousSubprojects)
                    if (similarity > 0.0) SlotCandidate(newIndex, slotIndex, similarity) else null
                }
                // Ties are broken by slot and then by bucket index so that the assignment only depends on
                // the two inputs, never on iteration order.
            }.sortedWith(compareByDescending<SlotCandidate> { it.similarity }.thenBy { it.slotIndex }.thenBy { it.newIndex })

    val slotOfBucket = arrayOfNulls<Int>(newBuckets.size)
    val takenSlots = mutableSetOf<Int>()
    candidates.forEach { candidate ->
        // Kept as two nested checks rather than one `&&`: `takenSlots.add` mutates, so folding it into a
        // short-circuiting condition would silently claim slots for already-assigned buckets if the
        // operands were ever reordered.
        if (slotOfBucket[candidate.newIndex] == null) {
            if (takenSlots.add(candidate.slotIndex)) {
                slotOfBucket[candidate.newIndex] = candidate.slotIndex
            }
        }
    }

    val freeSlots = previousSlots.indices.filter { it !in takenSlots }.iterator()
    var surplusSlot = previousSlots.size
    newBuckets.indices.forEach { newIndex ->
        if (slotOfBucket[newIndex] == null) {
            slotOfBucket[newIndex] = if (freeSlots.hasNext()) freeSlots.next() else surplusSlot++
        }
    }

    // Slots are unique, so sorting by them yields a total order. Buckets are compacted into a dense list:
    // when the new split has fewer buckets than the previous one the relative order is what survives.
    return newBuckets.indices.sortedBy { slotOfBucket[it]!! }.map { newBuckets[it] }
}

private class SlotCandidate(
    val newIndex: Int,
    val slotIndex: Int,
    val similarity: Double,
)

/**
 * Jaccard index of the two subproject sets: 1.0 for an unchanged bucket, 0.0 when they share nothing.
 */
private fun similarity(
    subprojects: Set<String>,
    previousSubprojects: Set<String>,
): Double {
    val shared = subprojects.count { it in previousSubprojects }
    return if (shared == 0) {
        0.0
    } else {
        shared.toDouble() / (subprojects.size + previousSubprojects.size - shared)
    }
}
