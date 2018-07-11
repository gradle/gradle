package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString

import org.junit.Assert.assertThat
import org.junit.Test


class KotlinBuildScriptIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `accepts lambdas as SAM argument to Kotlin function`() {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn("buildSrc", """

            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                `embedded-kotlin`
            }

            tasks.withType<KotlinCompile> {
                kotlinOptions {
                    freeCompilerArgs += listOf(
                        "-Xjsr305=strict",
                        "-XXLanguage:+NewInference",
                        "-XXLanguage:+SamConversionForKotlinFunctions"
                    )
                }
            }

            dependencies {
                compileOnly(gradleApi())
                compileOnly(kotlin("reflect"))
            }

            $repositoriesBlock

        """)

        withFile("buildSrc/src/main/kotlin/extensions.kt", """
            package extensions

            fun useAction(name: String, factory: org.gradle.api.Action<String>) = factory.execute(name)

            fun <T> create(name: String, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(name)

            fun <T : Any> createK(type: kotlin.reflect.KClass<T>, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(type.simpleName!!)

            fun test() {
                println(createK(String::class) { it.toUpperCase() })
                println(create("BAR") { it.toLowerCase() })
            }
        """)

        withBuildScript("""

            import extensions.*

            task("test") {
                doLast { println(create("foo", NamedDomainObjectFactory<String> { it.toUpperCase() })) }
                doLast { println(create<String>("foo", NamedDomainObjectFactory { it.toUpperCase() })) }
                doLast { println(create<String>("foo") { it.toUpperCase() }) }
                doLast { println(createK(String::class) { it.toUpperCase() }) }
                doLast { println(createK(String::class, { name: String -> name.toUpperCase() })) }
                doLast { test() }
                doLast { useAction("action") { println(toUpperCase()) } }
            }
         """.replaceIndent())

        assertThat(
            build("test", "-q").output.also(::println),
            containsString("ACTION"))
    }

    @Test
    fun `use of the plugins block on nested project block fails with reasonable error message`() {

        withBuildScript(
            """
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

        withBuildScript(
            """
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
