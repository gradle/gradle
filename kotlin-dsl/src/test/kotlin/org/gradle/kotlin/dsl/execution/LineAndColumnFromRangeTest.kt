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

package org.gradle.kotlin.dsl.execution

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class LineAndColumnFromRangeTest(val given: Given) {

    data class Given(val range: IntRange, val expected: Pair<Int, Int>)

    companion object {

        const val text = "line 1\nline 2\nline 3"

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic fun testCases(): Iterable<Given> =
            listOf(
                Given(0..0, 1 to 1),
                Given(1..1, 1 to 2),
                Given(7..7, 2 to 1),
                Given(8..8, 2 to 2),
                Given(19..19, 3 to 6)
            )
    }

    @Test
    fun test() {
        assertThat(
            text.lineAndColumnFromRange(given.range),
            equalTo(given.expected)
        )
    }
}
