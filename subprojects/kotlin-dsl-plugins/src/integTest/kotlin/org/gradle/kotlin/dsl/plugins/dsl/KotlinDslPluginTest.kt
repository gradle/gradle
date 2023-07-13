package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.kotlin.dsl.assignment.internal.KotlinDslAssignment
import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslPluginTest : AbstractPluginTest() {

    @Test
    fun `warns on unexpected kotlin-dsl plugin version`() {

        // The test applies the in-development version of the kotlin-dsl
        // which, by convention, it is always ahead of the version expected by
        // the in-development version of Gradle
        // (see publishedKotlinDslPluginsVersion in kotlin-dsl.gradle.kts)
        withKotlinDslPlugin()

        withDefaultSettings().appendText(
            """
            rootProject.name = "forty-two"
            """
        )

        val appliedKotlinDslPluginsVersion = futurePluginVersions["org.gradle.kotlin.kotlin-dsl"]
        build("help").apply {
            assertOutputContains(
                "This version of Gradle expects version '$expectedKotlinDslPluginsVersion' of the `kotlin-dsl` plugin but version '$appliedKotlinDslPluginsVersion' has been applied to root project 'forty-two'."
            )
        }
    }

    @Test
    fun `gradle kotlin dsl api dependency is added`() {

        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.kt",
            """

            // src/main/kotlin
            import org.gradle.kotlin.dsl.GradleDsl

            // src/generated
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `gradle kotlin dsl api is available for test implementation`() {

        assumeNonEmbeddedGradleExecuter() // Requires a Gradle distribution on the test-under-test classpath, but gradleApi() does not offer the full distribution

        withBuildScript(
            """

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            dependencies {
                testImplementation("junit:junit:4.13")
            }

            """
        )

        withFile(
            "src/main/kotlin/code.kt",
            """

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
            """
        )

        withFile(
            "src/test/kotlin/test.kt",
            """

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
            """
        )

        assertThat(
            outputOf("test", "-i"),
            containsString("Plugin Using Embedded Kotlin ")
        )
    }

    @Test
    fun `gradle kotlin dsl api is available in test-kit injected plugin classpath`() {
        assumeNonEmbeddedGradleExecuter() // requires a full distribution to run tests with test kit

        withBuildScript(
            """

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            dependencies {
                testImplementation("junit:junit:4.13")
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

            """
        )

        withFile(
            "src/main/kotlin/my/code.kt",
            """
            package my

            import org.gradle.api.*
            import org.gradle.kotlin.dsl.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    println("Plugin Using Embedded Kotlin " + embeddedKotlinVersion)
                }
            }
            """
        )

        withFile(
            "src/test/kotlin/test.kt",
            """

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
                        .withTestKitDir(File("${executer.gradleUserHomeDir.normalisedPath}"))
                        .withProjectDir(projectRoot)
                        .withPluginClasspath()
                        .forwardOutput()

                    // when:
                    val result = runner.withArguments("help").build()

                    // then:
                    require("Plugin Using Embedded Kotlin " in result.output)
                }
            }

            """
        )

        assertThat(
            outputOf("test", "-i"),
            containsString("Plugin Using Embedded Kotlin ")
        )
    }

    @Test
    fun `sam-with-receiver kotlin compiler plugin is applied to production code`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.kt",
            """

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

            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `can use SAM conversions for Kotlin functions without warnings`() {

        withBuildExercisingSamConversionForKotlinFunctions()

        build("test").apply {

            assertThat(
                output.also(::println),
                containsMultiLineString(
                    """
                    STRING
                    foo
                    bar
                    """
                )
            )

            assertThat(
                output,
                not(containsString(samConversionForKotlinFunctions))
            )
        }
    }

    @Test
    fun `kotlin assignment compiler plugin is applied to production code by default`() {
        withKotlinDslPlugin()
        withFile(
            "src/main/kotlin/code.kt",
            """

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.Property
            import org.gradle.kotlin.dsl.assign

            data class MyType(val property: Property<String>)

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    val myType = MyType(property = project.objects.property(String::class.java))
                    myType.property = "value"
                }
            }

            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `kotlin assignment compiler plugin is not applied to production code with opt-out`() {
        withKotlinDslPlugin()
        withFile("gradle.properties", "systemProp.${KotlinDslAssignment.ASSIGNMENT_SYSTEM_PROPERTY}=false")
        withFile(
            "src/main/kotlin/code.kt",
            """

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.Property
            import org.gradle.kotlin.dsl.assign

            data class MyType(val property: Property<String>)

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    val myType = MyType(property = project.objects.property(String::class.java))
                    myType.property = "value"
                }
            }

            """
        )

        buildAndFail("classes").apply {
            assertHasCause("Compilation error. See log for more details")
            assertHasErrorOutput("code.kt:13:21 Val cannot be reassigned")
            assertHasErrorOutput("code.kt:13:39 Type mismatch: inferred type is String but Property<String> was expected")
        }
    }

    private
    fun withBuildExercisingSamConversionForKotlinFunctions(buildSrcScript: String = "") {

        withDefaultSettingsIn("buildSrc")

        withBuildScriptIn(
            "buildSrc",
            """

            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            $buildSrcScript
            """
        )

        withFile(
            "buildSrc/src/main/kotlin/my.kt",
            """
            package my

            // Action<T> is a SAM with receiver
            fun <T : Any> applyActionTo(value: T, action: org.gradle.api.Action<T>) = action.execute(value)

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
            """
        )

        withBuildScript(
            """

            task("test") {
                doLast { my.test() }
            }

            """
        )
    }

    private
    fun outputOf(vararg arguments: String) =
        build(*arguments).output
}


private
const val samConversionForKotlinFunctions = "-XXLanguage:+SamConversionForKotlinFunctions"
