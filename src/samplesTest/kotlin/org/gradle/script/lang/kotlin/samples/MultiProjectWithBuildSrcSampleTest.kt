package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.toPlatformLineSeparators
import org.gradle.script.lang.kotlin.fixtures.trimTestIndent
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class MultiProjectWithBuildSrcSampleTest : AbstractSampleTest("multi-project-with-buildSrc") {

    @Test
    fun `multi-project-with-buildSrc`() {
        assertThat(
            build("hello").output,
            containsString("""
                :hello
                I'm multi-project-with-buildSrc
                :bluewhale:hello
                I'm bluewhale
                - I depend on water
                - I'm the largest animal that has ever lived on this planet.
                :krill:hello
                I'm krill
                - I depend on water
                - The weight of my species in summer is twice as heavy as all human beings.
            """.trimTestIndent().toPlatformLineSeparators()))
    }
}
