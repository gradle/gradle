package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.equalToMultiLineString

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

import java.io.StringWriter


class KotlinBuildScriptIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @ToBeFixedForConfigurationCache
    fun `can apply plugin using ObjectConfigurationAction syntax`() {

        withSettings(
            """
            rootProject.name = "foo"
            include("bar")
            """
        )

        withBuildScript(
            """

            open class ProjectPlugin : Plugin<Project> {
                override fun apply(target: Project) {
                    target.task("run") {
                        doLast { println(target.name + ":42") }
                    }
                }
            }

            apply { plugin<ProjectPlugin>() }

            subprojects {
                apply { plugin<ProjectPlugin>() }
            }
            """
        )

        assertThat(
            build("run", "-q").output,
            allOf(
                containsString("foo:42"),
                containsString("bar:42")
            )
        )
    }

    @Test
    fun `Project receiver is undecorated`() {

        withBuildScript(
            """
            fun Project.implicitReceiver() = this
            require(implicitReceiver() === rootProject)
            """
        )

        build("help")
    }

    @Test
    fun `scripts larger than 64KB are supported`() {

        withBuildScriptLargerThan64KB(
            """
            tasks.register("run") {
                doLast { println("*42*") }
            }
            """
        )

        assertThat(
            build("run").output,
            containsString("*42*")
        )
    }

    private
    fun withBuildScriptLargerThan64KB(suffix: String) =
        withBuildScript(
            StringWriter().run {
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
            }
        )

    @Test
    @ToBeFixedForConfigurationCache
    fun `can use Kotlin 1 dot 3 language features`() {

        withBuildScript(
            """

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
            """
        )

        assertThat(
            build("test", "-q").output,
            equalTo("42!")
        )
    }

    @Test
    @ToBeFixedForConfigurationCache
    fun `can use Kotlin 1 dot 4 language features`() {

        withBuildScript(
            """

            val myList = listOf(
                "foo",
                "bar", // trailing comma
            )

            task("test") {
                doLast {
                    print(myList)
                }
            }
            """
        )

        assertThat(
            build("test", "-q").output,
            equalTo("[foo, bar]")
        )
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
            """
        )

        buildAndFail("help").apply {
            assertThat(error, containsString("The plugins {} block must not be used here"))
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
            """
        )

        buildAndFail("help").apply {
            assertThat(error, containsString("The plugins {} block must not be used here"))
        }
    }

    @Test
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    @ToBeFixedForConfigurationCache(because = "Kotlin Gradle Plugin")
    fun `accepts lambda as SAM argument to Kotlin function`() {

        withKotlinBuildSrc()

        withFile(
            "buildSrc/src/main/kotlin/my.kt",
            """
            package my

            fun <T> applyActionTo(value: T, action: org.gradle.api.Action<T>) = action.execute(value)

            fun <T> create(name: String, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(name)

            fun <T : Any> create(type: kotlin.reflect.KClass<T>, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(type.simpleName!!)
            """
        )

        withBuildScript(
            """

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
            """.replaceIndent()
        )

        assertThat(
            build("test", "-q").output,
            containsMultiLineString(
                """
                FOO
                BAR
                BAZ
                STRING
                STRING
                ACTION
                """
            )
        )
    }

    @Test
    @ToBeFixedForConfigurationCache
    fun `can create fileTree from map for backward compatibility`() {

        val fileTreeFromMap = """
            fileTree(mapOf("dir" to ".", "include" to listOf("*.txt")))
                .joinToString { it.name }
        """

        withFile("foo.txt")

        val initScript = withFile(
            "init.gradle.kts",
            """
            println("INIT: " + $fileTreeFromMap)
            """
        )

        withSettings(
            """
            println("SETTINGS: " + $fileTreeFromMap)
            """
        )

        withBuildScript(
            """
            task("test") {
                doLast { println("PROJECT: " + $fileTreeFromMap) }
            }
            """
        )

        assertThat(
            build("test", "-q", "-I", initScript.absolutePath).output.trim(),
            equalToMultiLineString(
                """
                INIT: foo.txt
                SETTINGS: foo.txt
                PROJECT: foo.txt
                """.replaceIndent()
            )
        )
    }
}
