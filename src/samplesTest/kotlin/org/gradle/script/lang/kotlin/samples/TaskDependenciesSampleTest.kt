package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.toPlatformLineSeparators
import org.gradle.script.lang.kotlin.fixtures.trimTestIndent
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class TaskDependenciesSampleTest : AbstractSampleTest("task-dependencies") {

    @Test
    fun `default task`() {
        assertThat(
            build().output,
            containsString("""
                :hello
                Hello!
                :goodbye
                Goodbye!
                :chat
            """.trimTestIndent().toPlatformLineSeparators()))
    }

    @Test
    fun `mixItUp`() {
        assertThat(
            build("mixItUp").output,
            containsString("""
                :hello
                Hello!
                :goodbye
                Goodbye!
                :mixItUp
            """.trimTestIndent().toPlatformLineSeparators()))
    }
}
