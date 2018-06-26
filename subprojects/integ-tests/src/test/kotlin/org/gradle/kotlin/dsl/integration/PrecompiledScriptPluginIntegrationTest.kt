package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest

import org.junit.Ignore
import org.junit.Test


class PrecompiledScriptPluginIntegrationTest : AbstractPluginTest() {

    @Ignore("wip: Kotlin 1.2.60-eap-7")
    @Test
    fun `generated code follows kotlin-dsl coding conventions`() {

        withBuildScript("""
            plugins {
                `kotlin-dsl`
                `java-gradle-plugin`
                id("org.gradle.kotlin.ktlint-convention")
            }

            apply<org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins>()

            repositories { jcenter() }
        """)

        withFile("src/main/kotlin/plugin-without-package.gradle.kts")
        withFile("src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)

        build("generateScriptPluginAdapters")
        build("ktlintC")
    }

    override val testRepositoryPaths: List<String>
        get() = normalisedPathsOf(
            "../plugins/build/repository",
            "../plugins-experiments/build/repository")
}
