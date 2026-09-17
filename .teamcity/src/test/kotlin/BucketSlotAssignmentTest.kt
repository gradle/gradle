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

import model.PreviousBucketLayout
import model.assignToPreviousSlots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BucketSlotAssignmentTest {
    private fun assign(
        newBuckets: List<Set<String>>,
        previousSlots: List<Set<String>>,
    ): List<Set<String>> = assignToPreviousSlots(newBuckets, previousSlots) { it }

    private fun buckets(vararg buckets: String): List<Set<String>> = buckets.map { it.split(",").toSet() }

    @Test
    fun `unchanged buckets keep their previous slots`() {
        val previous = buckets("core", "dependency-management", "a,b,c")

        // The split algorithm orders buckets by measured test time, so unchanged buckets still show up
        // in a different order whenever the timings move a little.
        assertEquals(previous, assign(buckets("a,b,c", "core", "dependency-management"), previous))
    }

    @Test
    fun `keeps the computed order when there is no previous layout`() {
        val new = buckets("core", "a,b", "c")

        assertEquals(new, assign(new, emptyList()))
        assertEquals(new, assign(new, PreviousBucketLayout.EMPTY.slotsFor(1)))
    }

    @Test
    fun `a bucket that only changed slightly reclaims its slot`() {
        val previous = buckets("core", "a,b,c,d", "dependency-management")

        // "b" moved into the last bucket, which must not shuffle the whole file.
        assertEquals(
            buckets("core", "a,c,d", "b,dependency-management"),
            assign(buckets("b,dependency-management", "a,c,d", "core"), previous),
        )
    }

    @Test
    fun `an entirely new bucket takes a freed slot`() {
        val previous = buckets("core", "a,b", "dependency-management")

        assertEquals(
            buckets("core", "x,y", "dependency-management"),
            assign(buckets("core", "dependency-management", "x,y"), previous),
        )
    }

    @Test
    fun `surplus buckets are appended after the known slots`() {
        val previous = buckets("core", "a,b")

        assertEquals(
            buckets("core", "a,b", "x"),
            assign(buckets("x", "a,b", "core"), previous),
        )
    }

    @Test
    fun `a shrinking split keeps the relative order of the surviving buckets`() {
        val previous = buckets("core", "a,b", "dependency-management", "x,y")

        assertEquals(
            buckets("core", "dependency-management"),
            assign(buckets("dependency-management", "core"), previous),
        )
    }

    @Test
    fun `every bucket is emitted exactly once`() {
        val new = buckets("core", "a,b", "x", "dependency-management", "y,z")
        val previous = buckets("dependency-management", "a,b,c", "core")

        assertEquals(new.sortedBy { it.toString() }, assign(new, previous).sortedBy { it.toString() })
    }

    @Test
    fun `reads the bucket layout of a previously generated file`(
        @TempDir tempDir: File,
    ) {
        val layout = PreviousBucketLayout.readFrom(tempDir.resolve("test-buckets.json").apply { writeText(TEST_BUCKETS_JSON) })

        assertEquals(buckets("core", "a,b"), layout.slotsFor(1))
        assertEquals(buckets("dependency-management"), layout.slotsFor(14))
        assertEquals(emptyList<Set<String>>(), layout.slotsFor(99))
    }

    @Test
    fun `treats a missing or unreadable file as no previous layout`(
        @TempDir tempDir: File,
    ) {
        assertEquals(emptyList<Set<String>>(), PreviousBucketLayout.readFrom(tempDir.resolve("absent.json")).slotsFor(1))
        assertEquals(
            emptyList<Set<String>>(),
            PreviousBucketLayout.readFrom(tempDir.resolve("broken.json").apply { writeText("{ not json") }).slotsFor(1),
        )
    }

    companion object {
        private val TEST_BUCKETS_JSON =
            """
            [
              {
                "testCoverageUuid": 1,
                "buckets": [
                  { "subprojects": [ "core" ], "parallelizationMethod": { "name": "TestDistribution" } },
                  { "subprojects": [ "a", "b" ], "parallelizationMethod": { "name": "TestDistribution" } }
                ]
              },
              {
                "testCoverageUuid": 14,
                "buckets": [
                  {
                    "subprojects": [ "dependency-management" ],
                    "parallelizationMethod": { "name": "TeamCityParallelTests", "numberOfBatches": 2 }
                  }
                ]
              }
            ]
            """.trimIndent()
    }
}
