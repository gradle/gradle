/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.NoDaemonGradleExecuter
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class KotlinPluginAppliedWithOlderGradleVersionsIntegrationTest(
    private val gradleVersion: String,
    private val kgpVersion: String,
    private val kotlinLanguageVersion: String
) : AbstractIntegrationTest() {

    companion object {
        @Parameterized.Parameters(name = "Gradle {0}, KGP {1}, Kotlin {2}")
        @JvmStatic
        fun scenarios(): List<Array<Any>> = listOf(
            arrayOf("7.2", "1.9.22", "1.4"),
            arrayOf("7.6", "1.9.22", "1.4"),

            arrayOf("8.0", "1.9.22", "1.8"),
            arrayOf("8.7", "1.9.22", "1.8"),
            arrayOf("8.9", "1.9.23", "1.8"),
            arrayOf("8.10", "1.9.24", "1.8"),
            arrayOf("8.11", "2.0.20", "1.8"),
            arrayOf("8.12", "2.0.21", "1.8"),
            arrayOf("8.13", "2.1.0", "2.1"),
            arrayOf("8.14", "2.1.21", "2.1"),
            arrayOf("9.0.0", "2.2.0", "2.2"),
            arrayOf("9.2.0", "2.2.20", "2.2"),
            arrayOf("9.3.0", "2.2.21", "2.2"),
            arrayOf("9.4.0", "2.3.0", "2.2"),
            arrayOf("9.5.0", "2.3.20", "2.2"),

            arrayOf("9.6.0", "2.3.21", "2.2"),
            arrayOf("9.7.0-milestone-2", "2.4.0", "2.2"), // TODO: final version
            // arrayOf("9.8.0", "2.4.10", "2.2"), // TODO: once available
        )
    }

    @Test
    fun `plugin built with current Gradle can be applied with an older Gradle version`() {
        val gradleDistribution = buildContext.distribution(gradleVersion)
        val applyJdk = getHighestAvailableSupportedJdkForGradleVersion(gradleDistribution)

        val compileJdk = getJdkSuitableForKGPCompilation()
        buildPlugin(compileJdk)

        val result = applyPlugin(applyJdk)

        result.assertOutputContains("My Kotlin plugin applied!")
        result.assertOutputContains("My task executed!")
    }

    private fun buildPlugin(jdk: Jvm) {
        file("plugin/settings.gradle.kts").setText(
            """
            pluginManagement {
                repositories {
                    ${RepoScriptBlockUtil.mavenCentralRepositoryDefinition(KOTLIN)}
                }
            }
            rootProject.name = "plugin"
            """
        )
        file("plugin/build.gradle.kts").setText(
            """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget
            import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                kotlin("jvm") version "$kgpVersion"
                `java-gradle-plugin`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0"
            ${RepoScriptBlockUtil.mavenCentralRepository(KOTLIN)}

            gradlePlugin {
                plugins {
                    create("myPlugin") {
                        id = "my-plugin"
                        implementationClass = "com.example.MyPlugin"
                    }
                }
            }

            java {
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                    languageVersion = KotlinVersion.KOTLIN_${kotlinLanguageVersion.replace(".", "_")}
                    apiVersion = KotlinVersion.KOTLIN_${kotlinLanguageVersion.replace(".", "_")}
                    freeCompilerArgs.add("-Xskip-metadata-version-check") // Allow an older KGP to compile against the current Gradle API, whose Kotlin metadata may be newer than this compiler expects.
                }
            }

            publishing {
                repositories { maven { url = uri("${mavenRepo.uri}") } }
            }
            """
        )
        file("plugin/gradle.properties").setText(
            """
            # KGP 1.9.x registers its build statistics (FUS) listener via an unsupported provider, which is a configuration cache problem.
            enable_kotlin_performance_profile=false
            """
        )
        file("plugin/src/main/kotlin/com/example/MyPlugin.kt").setText(
            """
            package com.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    println("My Kotlin plugin applied!")
                    project.tasks.register("myTask") { task ->
                        task.doLast { println("My task executed!") }
                    }
                }
            }
            """
        )

        val result = inDirectory(file("plugin"))
            .withTasks("publish")
            .withJavaHome(jdk.javaHome.absolutePath)
            .noDeprecationChecks() // KGP emits deprecation warnings that vary by version and are not what we test here.
            .withStackTraceChecksDisabled() // The Kotlin compiler daemon intermittently crashes and logs a stack trace before falling back; that's not what we test here.
            .run()

        // KGP 2.0.20 and 2.0.21 register an attribute whose type is a plain enum
        // (KotlinNativeBundleArtifactFormat.KotlinNativeBundleArtifactsTypes). Gradle allows
        // this via a targeted compatibility exception in Attribute.of and emits a deprecation
        // warning. Verify the warning is present for those specific KGP versions and absent
        // for the neighboring 2.1.0 and 2.1.21 versions (which no longer use the plain enum).
        val kgpEnumDeprecationSummary =
            "Using the enum type KotlinNativeBundleArtifactsTypes as an attribute value type has been deprecated."
        if (kgpVersion == "2.0.20" || kgpVersion == "2.0.21") {
            result.assertOutputContains(kgpEnumDeprecationSummary)
        } else {
            check(!result.output.contains(kgpEnumDeprecationSummary)) {
                "KGP $kgpVersion unexpectedly emitted the KotlinNativeBundleArtifactsTypes deprecation. " +
                    "If KGP started re-using the plain enum, the special case in Attribute.of and the " +
                    "'upgrade to 2.1.0 or later' guidance in the upgrade guide need to be revisited."
            }
        }
    }

    private fun applyPlugin(jdk: Jvm): ExecutionResult {
        file("consumer/settings.gradle.kts").setText(
            """
            pluginManagement {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                    ${RepoScriptBlockUtil.mavenCentralRepositoryDefinition(KOTLIN)}
                }
            }
            rootProject.name = "consumer"
            """
        )
        file("consumer/build.gradle.kts").setText(
            """
            plugins {
                id("my-plugin") version "1.0"
            }
            """
        )

        val olderGradle = buildContext.distribution(gradleVersion)
        return NoDaemonGradleExecuter(olderGradle, testDirectoryProvider, buildContext)
            .usingProjectDirectory(file("consumer"))
            .withJavaHome(jdk.javaHome.absolutePath)
            .withTasks("myTask")
            .noDeprecationChecks()
            .run()
    }

    private fun getJdkSuitableForKGPCompilation(): Jvm {
        // The Gradle daemon building the plugin (i.e. current Gradle) must run on a JDK the KGP version under test can host its compiler on.
        // JDK 17 works for all KGP versions under test and is the minimum version currently required by Gradle.
        val jdk = AvailableJavaHomes.getJdk17()
        assumeTrue(jdk != null)
        return jdk!!
    }

    private fun getHighestAvailableSupportedJdkForGradleVersion(gradleDistribution: GradleDistribution): Jvm {
        val jdks = AvailableJavaHomes.getAvailableJdks { metadata -> gradleDistribution.daemonWorksWith(metadata.getJavaMajorVersion()) }
        jdks.sortByDescending { it.javaVersion }
        val jdk = jdks.firstOrNull()
        assumeTrue(jdk != null)
        return jdk!!
    }
}
