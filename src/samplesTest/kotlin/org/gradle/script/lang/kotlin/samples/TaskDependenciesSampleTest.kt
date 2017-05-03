package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.toPlatformLineSeparators
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class TaskDependenciesSampleTest : AbstractSampleTest("task-dependencies") {

    @Test
    fun `default task`() {
        assertThat(
            build().output.toPlatformLineSeparators(),
            containsString(":hello\nHello!\n:goodbye\nGoodbye!\n:chat"))
    }

    @Test
    fun `mixItUp`() {
        assertThat(
            build("mixItUp").output.toPlatformLineSeparators(),
            containsString(":hello\nHello!\n:goodbye\nGoodbye!\n:mixItUp"))
    }
}
