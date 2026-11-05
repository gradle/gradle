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

import configurations.ParallelizationMethod
import model.FunctionalTestBucket
import model.PreviousBucketLayout
import model.rebalanceIncrementally
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IncrementalBucketRebalancingTest {
    private val td = ParallelizationMethod.TestDistribution

    private fun bucket(
        subprojects: String,
        method: ParallelizationMethod = td,
    ) = FunctionalTestBucket(subprojects.split(","), method)

    private val previous =
        listOf(
            bucket("core", ParallelizationMethod.TeamCityParallelTests(4)),
            bucket("a,b,c"),
            bucket("d,e,f"),
            bucket("g,h,i"),
        )

    private fun times(vararg pairs: Pair<String, Int>) = mapOf("core" to 1000) + pairs.toMap()

    private val balancedTimes = times("a" to 10, "b" to 10, "c" to 10, "d" to 10, "e" to 10, "f" to 10, "g" to 10, "h" to 10, "i" to 10)

    private fun movedSubprojects(
        before: List<FunctionalTestBucket>,
        after: List<FunctionalTestBucket>,
    ): Int {
        val slotBefore = before.flatMapIndexed { index, bucket -> bucket.subprojects.map { it to index } }.toMap()
        return after.flatMapIndexed { index, bucket -> bucket.subprojects.filter { slotBefore[it] != index } }.size
    }

    @Test
    fun `unchanged timings keep the layout`() {
        assertEquals(previous, rebalanceIncrementally(previous, balancedTimes, td))
    }

    @Test
    fun `timing noise within tolerance keeps the layout`() {
        val noisy = times("a" to 12, "b" to 11, "c" to 9, "d" to 8, "e" to 10, "f" to 9, "g" to 11, "h" to 10, "i" to 9)

        assertEquals(previous, rebalanceIncrementally(previous, noisy, td))
    }

    @Test
    fun `an overloaded bucket moves a bounded number of subprojects`() {
        val skewed = balancedTimes + ("a" to 40)

        val result = rebalanceIncrementally(previous, skewed, td, maxMoves = 5)

        assertEquals(previous.size, result.size)
        assertEquals(previous[0], result[0])
        assertTrue(movedSubprojects(previous, result) in 1..5)
        assertEquals(previous.flatMap { it.subprojects }.toSet(), result.flatMap { it.subprojects }.toSet())
    }

    @Test
    fun `a new subproject only lands in the lightest bucket`() {
        val withNew = balancedTimes + ("c" to 5) + ("j" to 5)

        val result = rebalanceIncrementally(previous, withNew, td)

        assertEquals(listOf(previous[0], bucket("a,b,c,j"), previous[2], previous[3]), result)
    }

    @Test
    fun `a removed subproject only changes its own bucket`() {
        val withoutE = balancedTimes - "e"

        val result = rebalanceIncrementally(previous, withoutE, td)

        assertEquals(listOf(previous[0], previous[1], bucket("d,f"), previous[3]), result)
    }

    @Test
    fun `a single subproject bucket whose subproject is gone is dropped`() {
        val withoutCore = balancedTimes - "core"

        assertEquals(previous.drop(1), rebalanceIncrementally(previous, withoutCore, td))
    }

    @Test
    fun `the parallelization of large single subproject buckets is kept`() {
        val result = rebalanceIncrementally(previous, balancedTimes + ("core" to 5000), td)

        assertEquals(4, (result[0].parallelizationMethod as ParallelizationMethod.TeamCityParallelTests).numberOfBatches)
    }

    @Test
    fun `reads the parallelization method of previous buckets`(
        @TempDir tempDir: File,
    ) {
        val json =
            """
            [
              {
                "testCoverageUuid": 1,
                "buckets": [
                  { "subprojects": ["core"], "parallelizationMethod": { "name": "TeamCityParallelTests", "numberOfBatches": 3 } },
                  { "subprojects": ["a", "b"], "parallelizationMethod": { "name": "TestDistribution" } },
                  { "subprojects": ["c"] }
                ]
              }
            ]
            """.trimIndent()

        val layout = PreviousBucketLayout.readFrom(tempDir.resolve("test-buckets.json").apply { writeText(json) })

        val buckets = layout.bucketsFor(1)
        assertEquals(listOf(setOf("core"), setOf("a", "b"), setOf("c")), buckets.map { it.subprojects })
        assertEquals(listOf("TeamCityParallelTests", "TestDistribution", "None"), buckets.map { it.parallelizationMethod.name })
        assertEquals(3, (buckets[0].parallelizationMethod as ParallelizationMethod.TeamCityParallelTests).numberOfBatches)
    }
}
