package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class EmbeddedKotlinProviderTest : AbstractIntegrationTest() {

    @Test
    fun `no extra dependencies are added to the buildscript classpath`() {

        val result = build("buildEnvironment")

        assertThat(result.output, containsString("No dependencies"))
    }

    @Test
    fun `buildscript dependencies to embedded kotlin are resolved without an extra repository`() {

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib:$embeddedKotlinVersion")
                    classpath("org.jetbrains.kotlin:kotlin-reflect:$embeddedKotlinVersion")
                    classpath("org.jetbrains.kotlin:kotlin-compiler-embeddable:$embeddedKotlinVersion")
                    classpath("org.jetbrains.kotlin:kotlin-script-runtime:$embeddedKotlinVersion")
                    classpath("org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin:$embeddedKotlinVersion")
                }
            }
        """)

        val result = build("buildEnvironment")

        listOf("stdlib", "reflect", "compiler-embeddable", "script-runtime", "sam-with-receiver-compiler-plugin").forEach { module ->
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$module:$embeddedKotlinVersion"))
        }
    }

    @Test
    fun `stdlib and reflect are pinned to the embedded kotlin version`() {
        withBuildScript("""
            buildscript {
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib:1.0")
                    classpath("org.jetbrains.kotlin:kotlin-reflect:1.0")
                }
            }
        """)

        val result = build("buildEnvironment")

        listOf("stdlib", "reflect").forEach { module ->
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$module:1.0 -> $embeddedKotlinVersion"))
        }
    }

    @Test
    fun `compiler-embeddable is not pinned`() {
        withBuildScript("""
            buildscript {
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0")
                }
            }
        """)

        val result = buildAndFail("buildEnvironment")

        assertThat(result.output, containsString("Could not find org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0"))
    }
}
