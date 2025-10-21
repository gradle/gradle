/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.junit.Test

class JacocoTestKitKotlinScriptFingerprintingIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `running a test with TestKit that applies Jacoco won't brake KTS script fingerprinting`() {
        withSettings("""
            rootProject.name = "reproducer"
            
            dependencyResolutionManagement { 
                ${mavenCentralRepository(GradleDsl.KOTLIN)}
            }
        """.trimIndent())

        withBuildScript("""
            import org.gradle.util.internal.TextUtil
            
            plugins {
                `kotlin-dsl`
                jacoco
                id("com.gradle.plugin-publish") version ("2.0.0")
            }
            
            val jacocoRuntime by configurations.creating
            
            dependencies {
                testImplementation("junit:junit:4.13")
                testImplementation("org.hamcrest:hamcrest-library:1.3")
                testImplementation(gradleTestKit())
            
                jacocoRuntime("org.jacoco:org.jacoco.agent:${JacocoPlugin.DEFAULT_JACOCO_VERSION}:runtime")
            }
            
            gradlePlugin {
                plugins {
                    create("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "com.reproducer.MyPlugin"
                    }
                }
            }
            
            tasks.withType<Test> {
                val jacoco = the<JacocoTaskExtension>()
                systemProperty("jacocoAgentJar", TextUtil.normaliseFileSeparators(configurations.getByName("jacocoRuntime").singleFile.absolutePath))
            }
        """.trimIndent())

        withFile("src/main/kotlin/com/reproducer/MyPlugin.kt", $$"""
            package com.reproducer

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            
            class MyPlugin : Plugin<Settings> {
            
                override fun apply(settings: Settings) {
                    println("${MyPlugin::class.java.name} applied!")
                }
            }
        """.trimIndent())

        withFile("src/test/kotlin/com/reproducer/PluginTest.kt", $$"""
            package com.reproducer
            
            import java.io.File

            import org.gradle.testkit.runner.GradleRunner
            
            import org.junit.Rule
            import org.junit.Test
            import org.junit.rules.TemporaryFolder

            class PluginTest {

                @JvmField @Rule val temporaryFolder = TemporaryFolder()

                val projectDir by lazy {
                    File(temporaryFolder.root, "test").apply { mkdirs() }
                }

                @Test
                fun test() {
                    projectDir.resolve("settings.gradle.kts").writeText(
                        "plugins { id(\"my-plugin\") }\nrootProject.name = \"test\""
                    )
                    projectDir.resolve("gradle.properties").writeText(
                        "org.gradle.jvmargs=\"-javaagent:${System.getProperty("jacocoAgentJar")}"
                    )
                    projectDir.resolve("build.gradle.kts").writeText("")

                    val runner =
                        GradleRunner.create()
                            .withPluginClasspath()
                            .withArguments("help", "--stacktrace")
                            .forwardOutput()
                            .withProjectDir(projectDir)

                    val result = runner.build()

                    require("BUILD SUCCESSFUL" in result.output)
                }
            }
        """.trimIndent())

        build("test")
    }

}