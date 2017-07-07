package org.gradle.kotlin.dsl.samples

import org.junit.Test
import org.junit.Assert.assertTrue

import java.io.File


class GradlePluginSampleTest : AbstractSampleTest("gradle-plugin") {

    @Test
    fun `can use the plugin`() {
        val result = build("checkSample")

        assertTrue(File(projectRoot, "sample/build/copy/build.gradle.kts").isFile)
    }
}
