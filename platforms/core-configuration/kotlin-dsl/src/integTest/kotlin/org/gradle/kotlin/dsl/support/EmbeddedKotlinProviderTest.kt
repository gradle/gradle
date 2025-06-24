package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.Flaky
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.experimental.categories.Category


class EmbeddedKotlinProviderTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `no extra dependencies are added to the buildscript classpath`() {

        val result = build("buildEnvironment")

        assertThat(result.output, containsString("No dependencies"))
    }

    @Test
    fun `embedded kotlin dependencies are pinned to the embedded version`() {

        withBuildScript(
            """
            buildscript {
                $repositoriesBlock
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib:1.0")
                    classpath("org.jetbrains.kotlin:kotlin-reflect:1.0")
                }
            }
            """
        )

        val result = build("buildEnvironment")

        listOf("stdlib", "reflect").forEach { module ->
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$module:1.0 -> $embeddedKotlinVersion"))
        }
    }

    @Test
    @Category(Flaky::class) // https://github.com/gradle/gradle-private/issues/4723
    fun `stdlib and reflect are pinned to the embedded kotlin version for requested plugins`() {
        withBuildScript(
            """
            buildscript {
                $repositoriesBlock
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
                    classpath("org.jetbrains.kotlin:kotlin-reflect:1.9.21")
                }
            }
            plugins {
                kotlin("jvm") version "1.9.21"
            }
            """
        )

        executer.expectDocumentedDeprecationWarning(
            "The StartParameter.isConfigurationCacheRequested property has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                "Please use 'configurationCache.requested' property on 'BuildFeatures' service instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_startparameter_is_configuration_cache_requested",
        )

        val result = build("buildEnvironment")
        listOf("stdlib", "reflect").forEach { module ->
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$module:1.9.21 -> $embeddedKotlinVersion"))
        }
    }

    @Test
    fun `compiler-embeddable is not pinned`() {
        withBuildScript(
            """
            buildscript {
                $repositoriesBlock
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.3.31")
                }
            }
            """
        )

        val result = build("buildEnvironment")

        assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.3.31"))
        assertThat(result.output, not(containsString("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.3.31 ->")))
    }

    @Test
    fun `fails with a reasonable message on conflict with embedded kotlin`() {
        withBuildScript(
            """
            buildscript {
                $repositoriesBlock
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib") {
                        version { strictly("1.3.31") }
                    }
                }
            }
            """
        )

        val result = buildAndFail("buildEnvironment")

        assertThat(
            result.error,
            containsString("Cannot find a version of 'org.jetbrains.kotlin:kotlin-stdlib' that satisfies the version constraints")
        )
        assertThat(
            result.error,
            containsString("because of the following reason: Pinned to the embedded Kotlin")
        )
    }
}
