package org.gradle.kotlin.dsl.provider

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LineAndColumnFromRangeTest(val given: LineAndColumnFromRangeTest.Given) {

    data class Given(val range: IntRange, val expected: Pair<Int, Int>)

    companion object {

        const val text = "line 1\nline 2\nline 3"

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic fun testCases(): Iterable<LineAndColumnFromRangeTest.Given> =
            listOf(
                LineAndColumnFromRangeTest.Given(0..0, 1 to 1),
                LineAndColumnFromRangeTest.Given(1..1, 1 to 2),
                LineAndColumnFromRangeTest.Given(7..7, 2 to 1),
                LineAndColumnFromRangeTest.Given(8..8, 2 to 2),
                LineAndColumnFromRangeTest.Given(19..19, 3 to 6))
    }

    @Test
    fun test() {
        assertThat(
            LineAndColumnFromRangeTest.Companion.text.lineAndColumnFromRange(given.range),
            equalTo(given.expected))
    }
}
