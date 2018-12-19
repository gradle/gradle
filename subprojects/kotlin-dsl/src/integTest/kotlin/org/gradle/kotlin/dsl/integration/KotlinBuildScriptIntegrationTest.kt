package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test


class KotlinBuildScriptIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can use Kotlin 1 dot 3 language features`() {

        withBuildScript("""

            // Coroutines are no longer experimental
            val coroutine = sequence {
                // Unsigned integer types
                yield(42UL)
            }

            task("test") {
                doLast {
                    // Capturing when
                    when (val value = coroutine.first()) {
                        42UL -> print("42!")
                        else -> throw IllegalStateException()
                    }
                }
            }
        """)

        assertThat(
            build("test", "-q").output,
            equalTo("42!")
        )
    }

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
            assertThat(error, containsString("The plugins {} block must not be used here"))
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
            assertThat(error, containsString("The plugins {} block must not be used here"))
        }
    }

    @Test
    fun `accepts lambda as SAM argument to Kotlin function`() {

        assumeNonEmbeddedGradleExecuter()

        withKotlinBuildSrc()

        withFile("buildSrc/src/main/kotlin/my.kt", """
            package my

            fun <T> applyActionTo(value: T, action: org.gradle.api.Action<T>) = action.execute(value)

            fun <T> create(name: String, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(name)

            fun <T : Any> create(type: kotlin.reflect.KClass<T>, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(type.simpleName!!)
        """)

        withBuildScript("""

            import my.*

            task("test") {
                doLast {
                    // Explicit SAM conversion
                    println(create("foo", NamedDomainObjectFactory<String> { it.toUpperCase() }))
                    // Explicit SAM conversion with generic type argument inference
                    println(create<String>("bar", NamedDomainObjectFactory { it.toUpperCase() }))
                    // Implicit SAM conversion
                    println(create<String>("baz") { it.toUpperCase() })
                    println(create(String::class) { it.toUpperCase() })
                    println(create(String::class, { name: String -> name.toUpperCase() }))
                    // Implicit SAM with receiver conversion
                    applyActionTo("action") {
                        println(toUpperCase())
                    }
                }
            }
         """.replaceIndent())

        assertThat(
            build("test", "-q").output,
            containsMultiLineString("""
                FOO
                BAR
                BAZ
                STRING
                STRING
                ACTION
            """)
        )
    }
}
