package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.LeaksFileHandles

import org.junit.Test
import org.junit.Assert.assertTrue

import java.io.File


class GradlePluginSampleTest : AbstractSampleTest("gradle-plugin") {

    @Test
    @LeaksFileHandles
    fun `can use the plugin`() {

        build("consumer")

        assertTrue(File(projectRoot, "consumer/build/copy/build.gradle.kts").isFile)
    }
}
