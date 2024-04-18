/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.TestJavaClassUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.serialize.JavaClassUtil
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.kotlin.config.JvmTarget
import org.junit.Assume.assumeNotNull
import org.junit.Test


class KotlinDslJvmTargetIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `scripts are compiled using the build jvm target`() {

        withClassJar("utils.jar", JavaClassUtil::class.java)

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("utils.jar"))
                }
            }

            $printScriptJavaClassFileMajorVersion
        """)

        assertThat(build("help").output, containsString(outputFor(supportedKotlinJavaVersion())))
    }

    @Test
    fun `precompiled scripts use the build jvm target default`() {

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""
            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        assertThat(build("help").output, containsString(outputFor(supportedKotlinJavaVersion())))
    }

    @Test
    @Requires(UnitTestPreconditions.Jdk11OrLater::class)
    fun `can use a different jvmTarget to compile precompiled scripts`() {

        withClassJar("buildSrc/utils.jar", JavaClassUtil::class.java)

        withDefaultSettingsIn("buildSrc")
        withKotlinDslPluginIn("buildSrc").appendText("""

            java {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            kotlinDslPluginOptions {
                jvmTarget.set("11")
            }

            dependencies {
                implementation(files("utils.jar"))
            }
        """)

        withFile("buildSrc/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)
        withBuildScript("""plugins { id("some") }""")

        executer.expectDocumentedDeprecationWarning("The KotlinDslPluginOptions.jvmTarget property has been deprecated. This is scheduled to be removed in Gradle 9.0. Configure a Java Toolchain instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#kotlin_dsl_plugin_toolchains")

        assertThat(build("help").output, containsString(outputFor(JavaVersion.VERSION_11)))
    }

    @Test
    @LeaksFileHandles("Kotlin compiler daemon  taking time to shut down")
    fun `can use Java Toolchain to compile precompiled scripts`() {

        val currentJvm = Jvm.current()
        assumeNotNull(currentJvm)

        val newerJvm = AvailableJavaHomes.getDifferentVersion { it.languageVersion > currentJvm.javaVersion }
        assumeNotNull(newerJvm)

        val installationPaths = listOf(currentJvm!!, newerJvm!!)
            .joinToString(",") { it.javaHome.absolutePath }

        val utils = withClassJar("utils.jar", JavaClassUtil::class.java)
        mavenRepo.module("test", "utils", "1.0")
            .mainArtifact(mapOf("content" to utils.readBytes()))
            .publish()

        withDefaultSettingsIn("plugin")
        withBuildScriptIn("plugin", """
            plugins {
                `kotlin-dsl`
                `maven-publish`
            }
            group = "test"
            version = "1.0"
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(${newerJvm.javaVersion?.majorVersion}))
                }
            }
            dependencies {
                implementation("test:utils:1.0")
            }
            repositories {
                maven(url = "${mavenRepo.uri}")
                gradlePluginPortal()
            }
            publishing {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
            }
        """)
        withFile("plugin/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)

        gradleExecuterFor(arrayOf("check", "publish"), rootDir = file("plugin"))
            .withJavaHome(currentJvm.javaHome)
            .withArgument("-Porg.gradle.java.installations.paths=$installationPaths")
            .run()

        withSettingsIn("consumer", """
            pluginManagement {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.id == "some") {
                            useModule("test:plugin:1.0")
                        }
                    }
                }
            }
        """)
        withBuildScriptIn("consumer", """plugins { id("some") }""")

        val helpResult = gradleExecuterFor(arrayOf("help"), rootDir = file("consumer"))
            .withJavaHome(newerJvm.javaHome)
            .run()

        assertThat(helpResult.output, containsString(outputFor(supportedKotlinJavaVersion(newerJvm.javaVersion!!))))
    }

    @Test
    @LeaksFileHandles("Kotlin compiler daemon  taking time to shut down")
    fun `can use Java Toolchain to compile precompiled scripts on Java 22, with a warning`() {

        val currentJvm = Jvm.current()
        assumeNotNull(currentJvm)

        val newerJvm = AvailableJavaHomes.getJdk22()
        assumeNotNull(newerJvm)

        val installationPaths = listOf(currentJvm!!, newerJvm!!)
            .joinToString(",") { it.javaHome.absolutePath }

        val utils = withClassJar("utils.jar", JavaClassUtil::class.java)
        mavenRepo.module("test", "utils", "1.0")
            .mainArtifact(mapOf("content" to utils.readBytes()))
            .publish()

        withDefaultSettingsIn("plugin")
        withBuildScriptIn("plugin", """
            plugins {
                `kotlin-dsl`
                `maven-publish`
            }
            group = "test"
            version = "1.0"
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(${newerJvm.javaVersion?.majorVersion}))
                }
            }
            dependencies {
                implementation("test:utils:1.0")
            }
            repositories {
                maven(url = "${mavenRepo.uri}")
                gradlePluginPortal()
            }
            publishing {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
            }
        """)
        withFile("plugin/src/main/kotlin/some.gradle.kts", printScriptJavaClassFileMajorVersion)

        val pluginCompile = gradleExecuterFor(arrayOf("check", "publish"), rootDir = file("plugin"))
            .withJavaHome(currentJvm.javaHome)
            .withArgument("-Porg.gradle.java.installations.paths=$installationPaths")
            .run()
        assertThat(pluginCompile.output, containsString("w: Inconsistent JVM-target compatibility detected for tasks 'compileJava' (22) and 'compileKotlin' (21)."))

        withSettingsIn("consumer", """
            pluginManagement {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.id == "some") {
                            useModule("test:plugin:1.0")
                        }
                    }
                }
            }
        """)
        withBuildScriptIn("consumer", """plugins { id("some") }""")

        val helpResult = gradleExecuterFor(arrayOf("help"), rootDir = file("consumer"))
            .withJavaHome(newerJvm.javaHome)
            .run()

        assertThat(helpResult.output, containsString(outputFor(supportedKotlinJavaVersion(newerJvm.javaVersion!!))))
    }

    private
    val printScriptJavaClassFileMajorVersion = """
        println("Java Class Major Version = ${'$'}{org.gradle.internal.serialize.JavaClassUtil.getClassMajorVersion(this::class.java)}")
    """

    private
    fun outputFor(javaVersion: JavaVersion) =
        "Java Class Major Version = ${TestJavaClassUtil.getClassVersion(javaVersion)}"

    private
    fun supportedKotlinJavaVersion(version: JavaVersion = JavaVersion.current()): JavaVersion {
        val maxVersion = JavaVersion.forClassVersion(JvmTarget.supportedValues().last().majorVersion)
        return minOf(version, maxVersion)
    }
}
