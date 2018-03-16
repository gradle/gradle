package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString

import org.junit.Assert.assertThat
import org.junit.Test


class KotlintBuildScriptIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `use of the plugins block on nested project block fails with reasonable error message`() {

        withBuildScript("""
            plugins {
                id("base")
            }

            allprojects {
                plugins {
                    id("java-base")
                }
            }
        """)

        buildAndFail("help").apply {
            assertThat(output, containsString("The plugins {} block must not be used here"))
        }
    }

    @Test
    fun `non top-level use of the plugins block fails with reasonable error message`() {

        withBuildScript("""
            plugins {
                id("java-base")
            }

            dependencies {
                plugins {
                    id("java")
                }
            }
        """)

        buildAndFail("help").apply {
            assertThat(output, containsString("The plugins {} block must not be used here"))
        }
    }
}
