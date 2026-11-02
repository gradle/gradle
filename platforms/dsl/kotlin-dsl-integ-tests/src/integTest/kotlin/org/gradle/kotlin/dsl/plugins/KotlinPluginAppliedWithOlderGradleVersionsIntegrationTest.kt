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
import org.gradle.integtests.fixtures.versions.EmbeddedKotlinVersions
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.support.KOTLIN_DSL_LANGUAGE_VERSION
import org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
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
        @ClassRule
        @JvmField
        val pluginsDirectoryProvider = TestNameTestDirectoryProvider(KotlinPluginAppliedWithOlderGradleVersionsIntegrationTest::class.java)

        private val builtPluginVersions = mutableSetOf<String>()

        private val oldestTestedGradleVersion = GradleVersion.version("7.2")

        // The newest KGP that can still target the given Kotlin language version.
        private val kgpVersionByKotlinLanguageVersion = mapOf(
            "1.4" to "1.9.22",
            "1.8" to "2.2.21",
            KOTLIN_DSL_LANGUAGE_VERSION to embeddedKotlinVersion,
        )

        @Parameterized.Parameters(name = "KGP {1} targeting Kotlin {2} applied on Gradle {0}")
        @JvmStatic
        fun scenarios(): List<Array<Any>> {
            val embeddedKotlinVersions = EmbeddedKotlinVersions()
            checkCompatibilityMatrixMatchesKotlinDsl(embeddedKotlinVersions)
            return latestReleasedPatchOfEachMinorSince(oldestTestedGradleVersion)
                .groupBy { it.majorVersion to embeddedKotlinVersions.kotlinLanguageVersionOf(it) }
                .flatMap { (majorAndKotlinLanguageVersion, gradleVersions) ->
                    val kotlinLanguageVersion = majorAndKotlinLanguageVersion.second
                    val kgpVersion = checkNotNull(kgpVersionByKotlinLanguageVersion[kotlinLanguageVersion]) {
                        "Gradle ${gradleVersions.first().version} targets Kotlin $kotlinLanguageVersion. " +
                            "Add the newest KGP version that can still target Kotlin $kotlinLanguageVersion to kgpVersionByKotlinLanguageVersion."
                    }
                    setOf(gradleVersions.min(), gradleVersions.max()).map { gradleVersion ->
                        arrayOf(gradleVersion.version, kgpVersion, kotlinLanguageVersion)
                    }
                }
        }

        private fun checkCompatibilityMatrixMatchesKotlinDsl(embeddedKotlinVersions: EmbeddedKotlinVersions) {
            val documented = embeddedKotlinVersions.kotlinLanguageVersionOf(GradleVersion.current())
            check(documented == KOTLIN_DSL_LANGUAGE_VERSION) {
                "The compatibility matrix says that Gradle ${GradleVersion.current().version} targets Kotlin $documented, " +
                    "but the Kotlin DSL targets Kotlin $KOTLIN_DSL_LANGUAGE_VERSION. Update gradle/dependency-management/embedded-kotlin-versions.csv."
            }
        }

        private fun latestReleasedPatchOfEachMinorSince(oldest: GradleVersion): List<GradleVersion> =
            ReleasedVersionDistributions().all
                .map { it.version }
                .filter { it == it.baseVersion && it >= oldest }
                .groupBy { it.majorVersion to VersionNumber.parse(it.version).minor }
                .map { (_, patches) -> patches.max() }
                .sorted()
    }

    private val pluginVersion = "1.0-kgp$kgpVersion-kotlin$kotlinLanguageVersion"

    private val pluginRepoUri
        get() = pluginsDirectoryProvider.testDirectory.file("maven-repo").toURI()

    @Test
    fun `plugin built with current Gradle can be applied with an older Gradle version`() {
        val gradleDistribution = buildContext.distribution(gradleVersion)
        val applyJdk = getHighestAvailableSupportedJdkForGradleVersion(gradleDistribution)

        if (pluginVersion !in builtPluginVersions) {
            buildPlugin(getJdkSuitableForKGPCompilation())
            builtPluginVersions += pluginVersion
        }

        val result = applyPlugin(applyJdk)

        result.assertOutputContains("My Kotlin plugin applied!")
        result.assertOutputContains("My task executed with message: Hello from the consumer")
    }

    private fun buildPlugin(jdk: Jvm) {
        val pluginDir: TestFile = pluginsDirectoryProvider.testDirectory.file("plugin-$pluginVersion")
        pluginDir.file("settings.gradle.kts").setText(
            """
            pluginManagement {
                repositories {
                    ${RepoScriptBlockUtil.mavenCentralRepositoryDefinition(KOTLIN)}
                }
            }
            rootProject.name = "plugin"
            """
        )
        pluginDir.file("build.gradle.kts").setText(
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
            version = "$pluginVersion"
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
                repositories { maven { url = uri("$pluginRepoUri") } }
            }
            """
        )
        pluginDir.file("gradle.properties").setText(
            """
            # KGP 1.9.x registers its build statistics (FUS) listener via an unsupported provider, which is a configuration cache problem.
            enable_kotlin_performance_profile=false
            """
        )
        pluginDir.file("src/main/kotlin/com/example/MyPlugin.kt").setText(
            """
            package com.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.provider.Property

            abstract class MyExtension {
                abstract val message: Property<String>
            }

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    println("My Kotlin plugin applied!")
                    val extension = project.extensions.create("myExtension", MyExtension::class.java)
                    project.tasks.register("myTask") { task ->
                        task.doLast { println("My task executed with message: " + extension.message.get()) }
                    }
                }
            }
            """
        )

        inDirectory(pluginDir)
            .withTasks("publish")
            .withJavaHome(jdk.javaHome.absolutePath)
            .noDeprecationChecks() // KGP emits deprecation warnings that vary by version and are not what we test here.
            .withStackTraceChecksDisabled() // The Kotlin compiler daemon intermittently crashes and logs a stack trace before falling back; that's not what we test here.
            .run()

        pluginsDirectoryProvider.testDirectory.file("maven-repo/com/example/plugin/$pluginVersion/plugin-$pluginVersion.jar").assertIsFile()
    }

    private fun applyPlugin(jdk: Jvm): ExecutionResult {
        file("consumer/settings.gradle.kts").setText(
            """
            pluginManagement {
                repositories {
                    maven(url = "$pluginRepoUri")
                    ${RepoScriptBlockUtil.mavenCentralRepositoryDefinition(KOTLIN)}
                }
            }
            rootProject.name = "consumer"
            """
        )
        file("consumer/build.gradle.kts").setText(
            """
            plugins {
                id("my-plugin") version "$pluginVersion"
            }

            // Makes the older Gradle compile against the plugin's types, which reads their Kotlin metadata.
            myExtension {
                message.set("Hello from the consumer")
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
