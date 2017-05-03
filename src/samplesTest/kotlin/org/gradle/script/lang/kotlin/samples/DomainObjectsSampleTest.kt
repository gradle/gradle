package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.toPlatformLineSeparators
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class DomainObjectsSampleTest : AbstractSampleTest("domain-objects") {

    @Test
    fun `books task list all books and their path`() {
        assertThat(
            build("books").output.toPlatformLineSeparators(),
            containsString("""
                developerGuide -> src/docs/developerGuide
                quickStart -> src/docs/quick-start
                userGuide -> src/docs/userGuide""".trimIndent().trim()))
    }
}
