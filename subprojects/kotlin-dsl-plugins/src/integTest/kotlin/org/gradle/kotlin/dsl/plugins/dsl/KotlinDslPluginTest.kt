package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.ToBeFixedForVfsRetention

import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.TestPrecondition

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not

import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test


@ToBeFixedForVfsRetention(
    because = "https://github.com/gradle/gradle/issues/12184",
    failsOnlyIf = TestPrecondition.WINDOWS
)
@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslPluginTest : AbstractPluginTest() {

    @Before
    fun setupPluginTest() =
        requireGradleDistributionOnEmbeddedExecuter()

    @Test
    fun `warns on unexpected kotlin-dsl plugin version`() {

        // The test applies the in-development version of the kotlin-dsl
        // which, by convention, it is always ahead of the version expected by
        // the in-development version of Gradle
        // (see publishedKotlinDslPluginsVersion in kotlin-dsl.gradle.kts)
        withKotlinDslPlugin()

        withDefaultSettings().appendText("""
            rootProject.name = "forty-two"
        """)

        val appliedKotlinDslPluginsVersion = futurePluginVersions["org.gradle.kotlin.kotlin-dsl"]
        build("help").apply {
            assertOutputContains(
                "This version of Gradle expects version '$expectedKotlinDslPluginsVersion' of the `kotlin-dsl` plugin but version '$appliedKotlinDslPluginsVersion' has been applied to root project 'forty-two'."
            )
        }
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `gradle kotlin dsl api dependency is added`() {

        withKotlinDslPlugin()

        withFile("src/main/kotlin/code.kt", """

            // src/main/kotlin
            import org.gradle.kotlin.dsl.GradleDsl

            // src/generated
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

        """)

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `gradle kotlin dsl api is available for test implementation`() {

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            dependencies {
                testImplementation("junit:junit:4.12")
            }

        """)

        withFile("src/main/kotlin/code.kt", """

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        println("Plugin Using Embedded Kotlin " + embeddedKotlinVersion)
                    }
                }
            }
        """)

        withFile("src/test/kotlin/test.kt", """

            import org.gradle.testfixtures.ProjectBuilder
            import org.junit.Test
            import org.gradle.kotlin.dsl.*

            class MyTest {

                @Test
                fun `my test`() {
                    ProjectBuilder.builder().build().run {
                        apply<MyPlugin>()
                    }
                }
            }
        """)

        assertThat(
            outputOf("test", "-i"),
            containsString("Plugin Using Embedded Kotlin "))
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `gradle kotlin dsl api is available in test-kit injected plugin classpath`() {

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            dependencies {
                testImplementation("junit:junit:4.12")
                testImplementation("org.hamcrest:hamcrest-library:1.3")
                testImplementation(gradleTestKit())
            }

            gradlePlugin {
                plugins {
                    register("myPlugin") {
                        id = "my-plugin"
                        implementationClass = "my.MyPlugin"
                    }
                }
            }

        """)

        withFile("src/main/kotlin/my/code.kt", """
            package my

            import org.gradle.api.*
            import org.gradle.kotlin.dsl.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    println("Plugin Using Embedded Kotlin " + embeddedKotlinVersion)
                }
            }
        """)

        withFile("src/test/kotlin/test.kt", """

            import java.io.File

            import org.gradle.testkit.runner.GradleRunner

            import org.junit.Rule
            import org.junit.Test
            import org.junit.rules.TemporaryFolder

            class MyTest {

                @JvmField @Rule val temporaryFolder = TemporaryFolder()

                val projectRoot by lazy {
                    File(temporaryFolder.root, "test").apply { mkdirs() }
                }

                @Test
                fun `my test`() {
                    // given:
                    File(projectRoot, "build.gradle.kts")
                        .writeText("plugins { id(\"my-plugin\") }")

                    // and:
                    System.setProperty("org.gradle.daemon.idletimeout", "1000")
                    System.setProperty("org.gradle.daemon.registry.base", "${existing("daemons-registry").normalisedPath}")
                    File(projectRoot, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx128m")

                    // and:
                    val runner = GradleRunner.create()
                        .withGradleInstallation(File("${distribution.gradleHomeDir.normalisedPath}"))
                        .withProjectDir(projectRoot)
                        .withPluginClasspath()
                        .forwardOutput()

                    // when:
                    val result = runner.withArguments("help").build()

                    // then:
                    require("Plugin Using Embedded Kotlin " in result.output)
                }
            }

        """)

        assertThat(
            outputOf("test", "-i"),
            containsString("Plugin Using Embedded Kotlin "))
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `sam-with-receiver kotlin compiler plugin is applied to production code`() {

        withKotlinDslPlugin()

        withFile("src/main/kotlin/code.kt", """

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        copy {
                            from("build.gradle.kts")
                            into("build/build.gradle.kts.copy")
                        }
                    }
                }
            }

        """)

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    @ToBeFixedForInstantExecution(because = "Kotlin Gradle Plugin")
    fun `by default experimental Kotlin compiler features are enabled and a warning is issued`() {

        withBuildExercisingSamConversionForKotlinFunctions()

        build("test").apply {

            assertThat(
                output.also(::println),
                containsMultiLineString("""
                    STRING
                    foo
                    bar
                """)
            )

            assertThat(
                output,
                not(containsString(KotlinCompilerArguments.samConversionForKotlinFunctions))
            )

            assertThat(
                output,
                containsString(experimentalWarningFor(":buildSrc"))
            )
        }
    }

    @Test
    @ToBeFixedForInstantExecution(because = "Kotlin Gradle Plugin")
    fun `can explicitly disable experimental Kotlin compiler features warning`() {

        withBuildExercisingSamConversionForKotlinFunctions(
            "kotlinDslPluginOptions.experimentalWarning.set(false)"
        )

        build("test").apply {

            assertThat(
                output.also(::println),
                containsMultiLineString("""
                    STRING
                    foo
                    bar
                """)
            )

            assertThat(
                output,
                not(containsString(KotlinCompilerArguments.samConversionForKotlinFunctions))
            )

            assertThat(
                output,
                not(containsString(experimentalWarningFor(":buildSrc")))
            )
        }
    }

    private
    fun experimentalWarningFor(projectPath: String) =
        kotlinDslPluginExperimentalWarning(
            "project '$projectPath'",
            DocumentationRegistry().getDocumentationFor("kotlin_dsl", "sec:kotlin-dsl_plugin")
                .substringBefore("docs.gradle.org") // Dropping the Gradle Version
        )

    private
    fun withBuildExercisingSamConversionForKotlinFunctions(buildSrcScript: String = "") {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn("buildSrc", """

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            $buildSrcScript
        """)

        withFile("buildSrc/src/main/kotlin/my.kt", """
            package my

            // Action<T> is a SAM with receiver
            fun <T> applyActionTo(value: T, action: org.gradle.api.Action<T>) = action.execute(value)

            // NamedDomainObjectFactory<T> is a regular SAM
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

            task("test") {
                doLast { my.test() }
            }

         """)
    }

    private
    fun outputOf(vararg arguments: String) =
        build(*arguments).output
}
