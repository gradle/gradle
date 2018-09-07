package org.gradle.kotlin.dsl.samples

import org.junit.Assert
import org.junit.Test

import java.io.File


class PrecompiledScriptPluginSampleTest : AbstractSampleTest("precompiled-script-plugin") {

    @Test
    fun `can use the plugin`() {

        build("consumer")

        Assert.assertTrue(File(projectRoot, "consumer/build/copy/build.gradle.kts").isFile)
    }
}
