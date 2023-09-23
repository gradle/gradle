/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinDslPluginsIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import org.gradle.util.internal.ToBeImplemented
import org.junit.Test
import spock.lang.Issue


class GradleKotlinDslRegressionsTest : AbstractKotlinDslPluginsIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/9919")
    fun `gradleKotlinDsl dependency declaration does not throw`() {

        withBuildScript(
            """
            plugins { java }
            dependencies {
                compileOnly(gradleKotlinDsl())
            }
            """
        )

        build("help")
    }

    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-44303")
    fun `can configure ext extension`() {
        withBuildScript(
            """
            ext {
                set("foo", "bar")
            }
            """
        )

        build("help")
    }

    /**
     * When this issue gets fixed in a future Kotlin version, remove -XXLanguage:+DisableCompatibilityModeForNewInference from Kotlin DSL compiler arguments.
     */
    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-44303")
    @ToBeImplemented
    fun `kotlin resolution and inference issue KT-44303`() {
        withBuildScript("""
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins { `embedded-kotlin` }
            $repositoriesBlock
            dependencies {
                implementation(gradleKotlinDsl())
            }
        """)

        withFile("src/main/kotlin/code.kt", """
            import org.gradle.api.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    ext {
                        set("foo", "bar")
                    }
                }
            }
        """)

        val result = buildAndFail("classes")

        result.assertHasFailure("Execution failed for task ':compileKotlin'.") {
            it.assertHasCause("Compilation error. See log for more details")
        }
        result.assertHasErrorOutput("src/main/kotlin/code.kt:7:25 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch")
    }

    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55068")
    fun `kotlin ir backend issue kt-55068`() {

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            data class Container(val property: Property<String> = objects.property())
        """)
        withBuildScript("""plugins { id("my-plugin") }""")

        build("help")
    }

    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55065")
    fun `kotlin ir backend issue kt-55065`() {

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            tasks.withType<DefaultTask>().configureEach {
                val p: String by project
            }
        """)
        withBuildScript("""plugins { id("my-plugin") }""")

        build("help")
    }

    /**
     * When this issue gets fixed in a future Kotlin version, remove -XXLanguage:-TypeEnhancementImprovementsInStrictMode from Kotlin DSL compiler arguments.
     */
    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55542")
    @ToBeImplemented
    fun `nullable type parameters on non-nullable member works without disabling Koltlin type enhancement improvements in strict mode`() {
        withBuildScript("""
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins { `embedded-kotlin` }
            $repositoriesBlock
            dependencies {
                implementation(gradleKotlinDsl())
            }
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions.freeCompilerArgs.add("-Xjsr305=strict")
            }
        """)

        withFile("src/main/kotlin/code.kt", """
            import org.gradle.api.*

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project): Unit = project.run {
                    provider { "thing" }.map { null }
                }
            }
        """)

        val result = buildAndFail("classes")

        result.assertHasFailure("Execution failed for task ':compileKotlin'.") {
            it.assertHasCause("Compilation error. See log for more details")
        }
        result.assertHasErrorOutput("src/main/kotlin/code.kt:6:48 Null can not be a value of a non-null type Nothing")
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/8423")
    fun `non-static inner class for component metadata rule fails with a reasonable error message`() {
        withBuildScript("""
            plugins {
                id("java")
            }
            ${mavenCentralRepository(KOTLIN)}
            dependencies {
               components.all(FixOksocialOutput::class.java)
               implementation("com.baulsupp:oksocial-output:4.19.0")
            }
            open class FixOksocialOutput: ComponentMetadataRule {
               override fun execute(context: ComponentMetadataContext) = context.details.run {
                  if (id.group == "com.baulsupp" && id.name == "oksocial-output") {
                      allVariants {
                         withDependencies {
                            removeAll { name == "jackson-bom" }
                         }
                      }
                  }
               }
            }
        """)
        withFile("src/main/java/Main.java", "public class Main {}")

        buildAndFail("compileJava").apply {
            assertHasCause("Could not create an instance of type Build_gradle${'$'}FixOksocialOutput.")
            assertHasCause("Class Build_gradle.FixOksocialOutput is a non-static inner class.")
        }
    }

    @Test
    @Issue("https://youtrack.jetbrains.com/issue/KT-55880")
    @Issue("https://github.com/gradle/gradle/issues/23491")
    fun `compiling standalone scripts does not emit a warning at info level`() {
        withBuildScript("""println("test")""")
        build("help", "--info").apply {
            assertNotOutput("is not supposed to be used along with regular Kotlin sources, and will be ignored in the future versions by default. (Use -Xallow-any-scripts-in-source-roots command line option to opt-in for the old behavior.)")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/24481")
    fun `applied project scripts don't have project accessors`() {
        withFile("applied.gradle.kts", """
            println(java.sourceCompatibility)
        """)
        withBuildScript("""
            plugins { java }
            apply(from = "applied.gradle.kts")
        """)
        buildAndFail("help").apply {
            assertHasErrorOutput("Unresolved reference: sourceCompatibility")
        }

        withFile("applied.gradle.kts", """
            buildscript {
                dependencies {}
            }
            println(java.sourceCompatibility)
        """)
        buildAndFail("help").apply {
            assertHasErrorOutput("Unresolved reference: sourceCompatibility")
        }
    }
}
