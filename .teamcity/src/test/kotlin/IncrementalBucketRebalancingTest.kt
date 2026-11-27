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
import model.rebalanceIncrementally
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IncrementalBucketRebalancingTest {
    private val td = ParallelizationMethod.TestDistribution

    private val teamCityBatches = mutableMapOf<Int, ParallelizationMethod>()

    private fun batches(count: Int) = teamCityBatches.getOrPut(count) { ParallelizationMethod.TeamCityParallelTests(count) }

    private val parallelization = { count: Int -> if (count == 1) td else batches(count) }

    private fun bucket(
        subprojects: String,
        method: ParallelizationMethod = td,
    ) = FunctionalTestBucket(subprojects.split(","), method)

    private fun layout(vararg buckets: String) = buckets.map { it.split(",") }

    private val previousLayout = layout("core", "a,b,c", "d,e,f", "g,h,i")

    private val previous = listOf(bucket("core", batches(4)), bucket("a,b,c"), bucket("d,e,f"), bucket("g,h,i"))

    private fun times(vararg pairs: Pair<String, Int>) = mapOf("core" to 1000) + pairs.toMap()

    private val balancedTimes = times("a" to 10, "b" to 10, "c" to 10, "d" to 10, "e" to 10, "f" to 10, "g" to 10, "h" to 10, "i" to 10)

    private fun rebalance(
        layout: List<Collection<String>>,
        times: Map<String, Int>,
        expectedBucketNumber: Int = 4,
        maxSubprojectsPerBucket: Int = 11,
        maxMoves: Int = 5,
    ) = rebalanceIncrementally(layout, times, expectedBucketNumber, parallelization, maxSubprojectsPerBucket, maxMoves = maxMoves)

    private fun movedSubprojects(
        before: List<Collection<String>>,
        after: List<FunctionalTestBucket>,
    ): Int {
        val slotBefore = before.flatMapIndexed { index, bucket -> bucket.map { it to index } }.toMap()
        return after.flatMapIndexed { index, bucket -> bucket.subprojects.filter { slotBefore[it] != index } }.size
    }

    @Test
    fun `unchanged timings keep the layout`() {
        assertEquals(previous, rebalance(previousLayout, balancedTimes))
    }

    @Test
    fun `timing noise within tolerance keeps the layout`() {
        val noisy = times("a" to 12, "b" to 11, "c" to 9, "d" to 8, "e" to 10, "f" to 9, "g" to 11, "h" to 10, "i" to 9)

        assertEquals(previous, rebalance(previousLayout, noisy))
    }

    @Test
    fun `an overloaded bucket moves a subproject without leaving one alone`() {
        val result = rebalance(previousLayout, balancedTimes + ("a" to 40))

        assertEquals(listOf(previous[0], bucket("a,c"), bucket("b,d,e,f"), previous[3]), result)
    }

    @Test
    fun `a bucket too small to give up a subproject does not stop the others from rebalancing`() {
        val layout = layout("p1,p2", "q1,q2,q3,q4,q5,q6", "r1,r2")
        val times = mapOf("p1" to 60, "p2" to 60, "r1" to 5, "r2" to 5) + (1..6).associate { "q$it" to 15 }

        val result = rebalance(layout, times, expectedBucketNumber = 2)

        assertEquals(listOf(bucket("p1,p2"), bucket("q2,q3,q4,q5,q6"), bucket("q1,r1,r2")), result)
    }

    @Test
    fun `moves are capped per coverage`() {
        val heavy = (1..8).map { "x$it" }
        val layout = listOf(heavy, listOf("y1", "y2"), listOf("z1", "z2"))
        val times = heavy.associateWith { 10 } + mapOf("y1" to 1, "y2" to 1, "z1" to 1, "z2" to 1)

        val result = rebalance(layout, times, expectedBucketNumber = 1, maxMoves = 2)

        assertEquals(2, movedSubprojects(layout, result))
    }

    @Test
    fun `a new subproject only lands in the lightest bucket`() {
        val result = rebalance(previousLayout, balancedTimes + ("c" to 5) + ("j" to 5))

        assertEquals(listOf(previous[0], bucket("a,b,c,j"), previous[2], previous[3]), result)
    }

    @Test
    fun `new subprojects share a new bucket when every bucket is full`() {
        val result = rebalance(previousLayout, balancedTimes + ("j" to 5) + ("k" to 5), maxSubprojectsPerBucket = 3)

        assertEquals(previous + bucket("j,k"), result)
    }

    @Test
    fun `a removed subproject only changes its own bucket`() {
        val result = rebalance(previousLayout, balancedTimes - "e")

        assertEquals(listOf(previous[0], previous[1], bucket("d,f"), previous[3]), result)
    }

    @Test
    fun `the last bucket fills the slot of a removed one`() {
        val result = rebalance(previousLayout, balancedTimes - "core")

        assertEquals(listOf(previous[3], previous[1], previous[2]), result)
    }

    @Test
    fun `the batches of a large subproject follow its test time`() {
        val result = rebalance(previousLayout, balancedTimes + ("core" to 100))

        assertEquals(bucket("core", batches(2)), result[0])
    }

    @Test
    fun `a subproject alone keeps its bucket down to half the target load`() {
        val result = rebalance(previousLayout, balancedTimes + ("core" to 20))

        assertEquals(listOf(bucket("core"), previous[1], previous[2], previous[3]), result)
    }

    @Test
    fun `a subproject alone below half the target load joins the lightest bucket`() {
        val result = rebalance(previousLayout, balancedTimes + ("core" to 10))

        assertEquals(listOf(previous[3], bucket("a,b,c,core"), previous[2]), result)
    }

    @Test
    fun `a subproject that grows large gets its own bucket`() {
        val result = rebalance(previousLayout, balancedTimes + ("a" to 1000))

        assertEquals(listOf(bucket("core", batches(2)), bucket("b,c"), previous[2], previous[3], bucket("a", batches(2))), result)
    }

    @Test
    fun `a layout generated without test times is grouped once times exist`() {
        val result = rebalance(layout("core", "a", "b", "c", "d", "e", "f", "g", "h", "i"), balancedTimes)

        assertEquals(listOf(previous[0], bucket("a,b,c,d,e,f,g,h,i")), result)
    }

    private val overflowLayout = layout("a,b,c", "d,e,f", "o1,o2,o3,o4,o5,o6")

    private val overflowTimes =
        mapOf("a" to 10, "b" to 10, "c" to 10, "d" to 10, "e" to 10, "f" to 10) + (1..6).associate { "o$it" to 30 }

    @Test
    fun `a bucket far above the target load is split by load`() {
        val result = rebalance(overflowLayout, overflowTimes)

        assertEquals(listOf(bucket("a,b,c"), bucket("d,e,f")), result.take(2))
        assertEquals(5, result.size)
        assertTrue(result.drop(2).all { it.subprojects.size == 2 && it.parallelizationMethod == td })
        assertEquals(overflowLayout[2].toSet(), result.drop(2).flatMap { it.subprojects }.toSet())
    }

    @Test
    fun `a bucket over the subproject cap but below the target load is kept`() {
        val light = (1..20).map { "l$it" }

        val result = rebalance(previousLayout + listOf(light), balancedTimes + light.associateWith { 1 })

        assertEquals(previous + bucket(light.joinToString(",")), result)
    }

    @Test
    fun `a regenerated layout is stable`() {
        listOf(
            previousLayout to balancedTimes + ("a" to 40),
            previousLayout to balancedTimes + ("a" to 1000),
            previousLayout to balancedTimes + ("core" to 10),
            previousLayout to balancedTimes - "core",
            layout("core", "a", "b", "c", "d", "e", "f", "g", "h", "i") to balancedTimes,
            overflowLayout to overflowTimes,
        ).forEach { (layout, times) ->
            val once = rebalance(layout, times)

            assertEquals(once, rebalance(once.map { it.subprojects }, times))
        }
    }
}
