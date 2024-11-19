import model.splitIntoBuckets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.LinkedList

/*
 * Copyright 2024 the original author or authors.
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

data class Subproject(val name: String, val size: Int)
interface SubprojectBucket
data class SmallSubprojectCombination(val subprojects: List<Subproject>) : SubprojectBucket
data class LargeSubprojectSplit(val subproject: Subproject, val split: Int) : SubprojectBucket

class SplitBucketTest {
    @Test
    fun testUnbalancedBuckets() {
        val subprojects = LinkedList(
            listOf(
                Subproject("a", 20),
                Subproject("b", 19),
                Subproject("c", 5),
                Subproject("d", 5),
                Subproject("e", 5),
                Subproject("f", 5),
                Subproject("g", 4),
                Subproject("h", 4),
                Subproject("i", 4),
                Subproject("j", 4),
            )
        )

        val buckets = splitIntoBuckets(
            list = subprojects,
            toIntFunction = { it: Subproject -> it.size },
            largeElementSplitFunction = { subproject, split ->
                List(split) { LargeSubprojectSplit(subproject, split) }
            },
            smallElementAggregateFunction = { SmallSubprojectCombination(it) },
            expectedBucketNumber = 5,
            maxNumberInBucket = 3
        )

        assertTrue(buckets.size == 5)
        assertEquals("LargeSubprojectSplit(subproject=Subproject(name=a, size=20), split=1)", buckets[0].toString())
        assertEquals("LargeSubprojectSplit(subproject=Subproject(name=b, size=19), split=1)", buckets[1].toString())
        assertEquals(
            "SmallSubprojectCombination(subprojects=[Subproject(name=c, size=5), Subproject(name=j, size=4), Subproject(name=i, size=4)])",
            buckets[2].toString()
        )
        assertEquals(
            "SmallSubprojectCombination(subprojects=[Subproject(name=d, size=5), Subproject(name=h, size=4), Subproject(name=g, size=4)])",
            buckets[3].toString()
        )
        assertEquals(
            "SmallSubprojectCombination(subprojects=[Subproject(name=e, size=5), Subproject(name=f, size=5)])",
            buckets[4].toString()
        )
    }
}
