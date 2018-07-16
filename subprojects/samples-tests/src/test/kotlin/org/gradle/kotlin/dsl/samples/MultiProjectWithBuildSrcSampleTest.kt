package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.junit.Assert.assertThat
import org.junit.Test


class MultiProjectWithBuildSrcSampleTest : AbstractSampleTest("multi-project-with-buildSrc") {

    @Test
    fun `multi-project-with-buildSrc`() {
        assertThat(
            build("hello").output,
            containsMultiLineString("""
                > Task :hello
                I'm ${testName.methodName}

                > Task :bluewhale:hello
                I'm bluewhale
                - I depend on water
                - I'm the largest animal that has ever lived on this planet.

                > Task :krill:hello
                I'm krill
                - I depend on water
                - The weight of my species in summer is twice as heavy as all human beings.
            """))
    }
}
