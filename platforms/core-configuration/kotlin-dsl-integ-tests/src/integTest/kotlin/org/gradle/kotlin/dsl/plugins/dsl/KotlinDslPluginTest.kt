/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslPluginTest : AbstractKotlinIntegrationTest() {

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

        build("help").apply {
            assertOutputContains(
                "This version of Gradle expects version '$expectedKotlinDslPluginsVersion' of the `kotlin-dsl` plugin but version '$futureKotlinDslPluginVersion' has been applied to root project 'forty-two'."
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
    @Requires(
        IntegTestPreconditions.NotEmbeddedExecutor::class,
        reason = "Requires a Gradle distribution on the test-under-test classpath, but gradleApi() does not offer the full distribution"
    )
    fun `gradle kotlin dsl api is available for test implementation`() {

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

        build("test")

        val results = DefaultTestExecutionResult(testDirectory)
        results.assertTestClassesExecuted("MyTest")
        results.testClass("MyTest").assertStdout(containsString("Plugin Using Embedded Kotlin "))
    }

    @Test
    @Requires(
        IntegTestPreconditions.NotEmbeddedExecutor::class,
        reason = "requires a full distribution to run tests with test kit"
    )
    fun `gradle kotlin dsl api is available in test-kit injected plugin classpath`() {

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

        build("test")

        val results = DefaultTestExecutionResult(testDirectory)
        results.assertTestClassesExecuted("MyTest")
        results.testClass("MyTest").assertStdout(containsString("Plugin Using Embedded Kotlin "))
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
}


private
const val samConversionForKotlinFunctions = "-XXLanguage:+SamConversionForKotlinFunctions"
