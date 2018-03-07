package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.junit.Test


class KotlinSettingsScriptIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `Settings script path is resolved relative to parent script dir`() {

        withFile("gradle/my.settings.gradle.kts", """
            apply { from("./answer.settings.gradle.kts") }
        """)

        withFile("gradle/answer.settings.gradle.kts", """
            gradle.rootProject {
                val answer by extra { "42" }
            }
        """)

        withSettings("""
            apply { from("gradle/my.settings.gradle.kts") }
        """)

        withBuildScript("""
            val answer: String by extra
            println("*" + answer + "*")
        """)

        assert(build().output.contains("*42*"))
    }
}
