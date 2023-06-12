package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.clickableUrlFor
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.GradleVersion

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.StringWriter


class KotlinBuildScriptIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
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
                        val projectName = target.name
                        doLast { println(projectName + ":42") }
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
    fun `can use Kotlin 1 dot 3 language features`() {

        withBuildScript(
            """

            task("test") {
                doLast {

                    // Coroutines are no longer experimental
                    val coroutine = sequence {
                        // Unsigned integer types
                        yield(42UL)
                    }

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
    fun `can use Kotlin 1 dot 4 language features`() {

        withBuildScript(
            """

            task("test") {
                doLast {

                    val myList = listOf(
                        "foo",
                        "bar", // trailing comma
                    )

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
    fun `accepts lambda as SAM argument to Kotlin function`() {

        withKotlinBuildSrc()

        withFile(
            "buildSrc/src/main/kotlin/my.kt",
            """
            package my

            fun <T : Any> applyActionTo(value: T, action: org.gradle.api.Action<T>) = action.execute(value)

            fun <T : Any> create(name: String, factory: org.gradle.api.NamedDomainObjectFactory<T>): T = factory.create(name)

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
            """.trimIndent()
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
                val ft = $fileTreeFromMap
                doLast { println("PROJECT: " + ft) }
            }
            """
        )

        assertThat(
            build("test", "-q", "-I", initScript.absolutePath).output.trim(),
            equalTo(
                """
                INIT: foo.txt
                SETTINGS: foo.txt
                PROJECT: foo.txt
                """.trimIndent()
            )
        )
    }

    @Test
    fun `can access project extensions`() {
        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/MyExtension.kt", """
            interface MyExtension {
                fun some(message: String) { println(message) }
            }
        """)
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            extensions.create<MyExtension>("my")
            tasks.register("noop")
        """)
        withBuildScript("""
            plugins { id("my-plugin") }

            extensions.getByType(MyExtension::class).some("api.get")
            extensions.configure<MyExtension> { some("api.configure") }
            the<MyExtension>().some("kotlin.reified.get")
            the(MyExtension::class).some("kotlin.kclass.get")
            configure<MyExtension> { some("kotlin.configure") }
            my.some("accessor.get")
            my { some("accessor.configure") }
        """)

        assertThat(
            build("noop", "-q").output.trim(),
            equalTo(
                """
                api.get
                api.configure
                kotlin.reified.get
                kotlin.kclass.get
                kotlin.configure
                accessor.get
                accessor.configure
                """.trimIndent()
            )
        )

        // Deprecation warnings assertion
        build("noop")
    }

    @Test
    fun `accessing absent extension fails with reasonable error message`() {
        listOf(
            "the<SourceDirectorySet>()",
            "the(SourceDirectorySet::class)",
            "configure<SourceDirectorySet> {}",
        ).forEach { accessFlavor ->
            withBuildScript(accessFlavor)
            buildAndFail("help").apply {
                assertHasFailure("Extension of type 'SourceDirectorySet' does not exist. Currently registered extension types: [ExtraPropertiesExtension]") {}
            }
        }
    }

    @Test
    @UnsupportedWithConfigurationCache(because = "test configuration phase")
    fun `can access project conventions`() {
        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/MyConvention.kt", """
            interface MyConvention {
                fun some(message: String) { println(message) }
            }
        """)
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            convention.plugins["my"] = objects.newInstance<MyConvention>()
            tasks.register("noop")
        """)
        withBuildScript("""
            plugins { id("my-plugin") }

            convention.getPlugin(MyConvention::class).some("api.get")
            the<MyConvention>().some("kotlin.reified.get")
            the(MyConvention::class).some("kotlin.kclass.get")
            configure<MyConvention> { some("kotlin.configure") }
        """)

        assertThat(
            build("noop", "-q").output.trim(),
            equalTo(
                """
                api.get
                kotlin.reified.get
                kotlin.kclass.get
                kotlin.configure
                """.trimIndent()
            )
        )

        // Deprecation warnings assertion
        repeat(4) {
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.Convention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
        }
        repeat(5) {
            executer.expectDocumentedDeprecationWarning(
                "The Project.getConvention() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
        }
        build("noop")
    }

    @Test
    fun `script compilation warnings are output on the console`() {
        val script = withBuildScript("""
            @Deprecated("BECAUSE")
            fun deprecatedFunction() {}
            deprecatedFunction()
        """)
        build("help").apply {
            assertOutputContains("w: ${clickableUrlFor(script)}:4:13: 'deprecatedFunction(): Unit' is deprecated. BECAUSE")
        }
    }
}
