package org.gradle.kotlin.dsl.samples

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class CopySampleTest : AbstractSampleTest("copy") {

    @Test
    fun `initConfig task copy spec satisfied`() {
        // when:
        build("initConfig")

        // then:
        val root = projectRoot.toPath().resolve("build").resolve("target").resolve("config")
        listOf("copy.data", "copy.xml").map { root.resolve(it).toFile() }.forEach {
            assertTrue("File copied $it", it.exists())
        }
        listOf("copy.bak", "copy.txt").map { root.resolve(it).toFile() }.forEach {
            assertFalse("File not copied $it", it.exists())
        }
    }
}
