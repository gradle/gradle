package org.gradle.kotlin.dsl.samples

import org.junit.Test
import org.junit.Assert.assertTrue

import java.io.File


class GradlePluginSampleTest : AbstractSampleTest("gradle-plugin") {

    @Test
    fun `can use the plugin`() {

        build("checkConsumer")

        assertTrue(File(projectRoot, "consumer/build/copy/build.gradle.kts").isFile)
    }
}
