package org.gradle.kotlin.dsl.samples

import org.junit.Test
import java.io.File


class AntSampleTest : AbstractSampleTest("ant") {

    @Test
    fun `hello ant task`() {

        // when:
        val result = build("hello")

        // then:
        result.output.contains("Hello from Ant!")
    }

    @Test
    fun `zip ant task`() {

        // when:
        build("zip")

        // then:
        File(projectRoot, "build/archive.zip").isFile
    }

    @Test
    fun `custom pmd ant task`() {

        // expect:
        build("pmd")
    }
}
