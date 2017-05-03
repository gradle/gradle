package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.toPlatformLineSeparators
import org.gradle.script.lang.kotlin.fixtures.trimTestIndent
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File


class DomainObjectsSampleTest : AbstractSampleTest("domain-objects") {

    @Test
    fun `books task list all books and their path`() {
        assertThat(
            build("books").output,
            containsString("""
                developerGuide -> src${File.separator}docs${File.separator}developerGuide
                quickStart -> src${File.separator}docs${File.separator}quick-start
                userGuide -> src${File.separator}docs${File.separator}userGuide
            """.trimTestIndent().toPlatformLineSeparators()))
    }
}
