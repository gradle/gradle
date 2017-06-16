package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString

import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File


class ProviderPropertiesSampleTest : AbstractSampleTest("provider-properties") {

    @Test
    fun `hello task logs and write message to files`() {
        // when:
        val output = build("hello", "-i").output

        // then:
        val message = "Hi from Gradle"
        val files = listOf("a.txt", "b.txt").map { File(File(projectRoot, "build"), it).canonicalFile }
        assertThat(output, containsString("Writing message '$message' to files $files"))
        files.forEach {
            assertThat(it.readText(), containsString(message))
        }
    }
}
