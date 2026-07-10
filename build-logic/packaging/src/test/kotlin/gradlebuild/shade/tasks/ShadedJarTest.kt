/*
 * Copyright 2018 the original author or authors.
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

package gradlebuild.shade.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal
class ShadedJarTest {
    @Test
    fun aggregates_class_trees() {
        val result = buildClassTrees(
            listOf(
                mapOf("First.class" to emptyList()),
                mapOf(
                    "First.class" to listOf("Second.class", "Third.class"),
                    "Second.class" to emptyList(),
                    "Third.class" to emptyList()
                ),
                mapOf(
                    "First.class" to listOf("Forth.class"),
                    "Third.class" to listOf("First.class"),
                    "Forth.class" to emptyList()
                )
            )
        )

        assertEquals(
            mapOf(
                "First.class" to setOf("Second.class", "Third.class", "Forth.class"),
                "Second.class" to emptySet(),
                "Third.class" to setOf("First.class"),
                "Forth.class" to emptySet()
            ),
            result
        )
    }
}
