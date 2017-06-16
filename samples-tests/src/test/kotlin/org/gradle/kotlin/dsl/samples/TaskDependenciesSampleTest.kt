package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.junit.Assert.assertThat
import org.junit.Test


class TaskDependenciesSampleTest : AbstractSampleTest("task-dependencies") {

    @Test
    fun `default task`() {
        assertThat(
            build().output,
            containsMultiLineString("""
                :hello
                Hello!
                :goodbye
                Goodbye!
                :chat
            """))
    }

    @Test
    fun `mixItUp`() {
        assertThat(
            build("mixItUp").output,
            containsMultiLineString("""
                :hello
                Hello!
                :goodbye
                Goodbye!
                :mixItUp
            """))
    }
}
