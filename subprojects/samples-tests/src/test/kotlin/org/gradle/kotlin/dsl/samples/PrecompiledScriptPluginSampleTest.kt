package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.LeaksFileHandles
import org.junit.Assert
import org.junit.Test
import java.io.File


class PrecompiledScriptPluginSampleTest : AbstractSampleTest("precompiled-script-plugin") {

    @Test
    @LeaksFileHandles
    fun `can use the plugin`() {

        build("consumer")

        Assert.assertTrue(File(projectRoot, "consumer/build/copy/build.gradle.kts").isFile)
    }
}
