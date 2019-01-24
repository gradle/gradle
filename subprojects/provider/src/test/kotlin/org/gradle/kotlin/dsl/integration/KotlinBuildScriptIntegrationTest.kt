package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test

import java.io.StringWriter


class KotlinBuildScriptIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `scripts larger than 64KB are supported`() {

        withBuildScriptLargerThan64KB("""
            tasks.register("run") {
                doLast { println("*42*") }
            }
        """)

        assertThat(
            build("run").output,
            containsString("*42*")
        )
    }

    private
    fun withBuildScriptLargerThan64KB(suffix: String) =
        withBuildScript(StringWriter().run {
            var bytesWritten = 0
            var i = 0
            while (bytesWritten < 64 * 1024) {
                val stmt = "val v$i = $i\n"
                write(stmt)
                i += 1
                bytesWritten += stmt.toByteArray().size
            }
            write(suffix)
            toString()
        })

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
