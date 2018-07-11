package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString

import org.junit.Assert.assertThat
import org.junit.Test


class KotlinBuildScriptIntegrationTest : AbstractIntegrationTest() {

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

    @Test
    fun `accepts lambda as SAM argument to Kotlin function`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn("buildSrc", """

            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                `kotlin-dsl`
            }

            tasks.withType<KotlinCompile> {
                kotlinOptions {
                    freeCompilerArgs += listOf(
                        "-XXLanguage:+NewInference",
                        "-XXLanguage:+SamConversionForKotlinFunctions"
                    )
                }
            }

        """)

        withFile("buildSrc/src/main/kotlin/my.kt", """
            package my

            fun <T> applyActionTo(value: T, action: org.gradle.api.Action<T>) = action.execute(value)

            fun <T> create(name: String, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(name)

            fun <T : Any> createK(type: kotlin.reflect.KClass<T>, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(type.simpleName!!)

            fun test() {

                // Implicit SAM conversion in regular source
                println(createK(String::class) { it.toUpperCase() })
                println(create("FOO") { it.toLowerCase() })

                // Implicit SAM with receiver conversion in regular source
                applyActionTo("BAR") {
                    println(toLowerCase())
                }
            }
        """)

        withBuildScript("""

            import my.*

            task("test") {
                doLast {
                    // Explicit SAM conversion
                    println(create("foo", NamedDomainObjectFactory<String> { it.toUpperCase() }))
                    // Explicit SAM conversion with generic type argument inference
                    println(create<String>("foo", NamedDomainObjectFactory { it.toUpperCase() }))
                    // Implicit SAM conversion
                    println(create<String>("foo") { it.toUpperCase() })
                    println(createK(String::class) { it.toUpperCase() })
                    println(createK(String::class, { name: String -> name.toUpperCase() }))
                    // Implicit SAM with receiver conversion
                    applyActionTo("action") {
                        println(toUpperCase())
                    }
                    test()
                }
            }
         """.replaceIndent())

        assertThat(
            build("test", "-q").output.also(::println),
            containsString("ACTION"))
    }
}
