package org.gradle.kotlin.dsl.integration

import org.junit.Test


class PrecompiledScriptPluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `generated code follows kotlin-dsl coding conventions`() {

        withBuildScript("""
            plugins {
                `kotlin-dsl`
                id("org.gradle.kotlin.ktlint-convention")
            }

            repositories { jcenter() }
        """)

        withFile("src/main/kotlin/plugin-without-package.gradle.kts")
        withFile("src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)

        build("generateScriptPluginAdapters")
        build("ktlintC")
    }
}
