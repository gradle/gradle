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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue
import spock.lang.TempDir

class JacocoTestKitKotlinScriptFingerprintingIntegrationTest extends AbstractIntegrationSpec {

    @TempDir
    File jacocoDestinationDir

    @Issue("https://github.com/gradle/gradle/issues/34942")
    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "Testing build using a TestKit")
    def "running a test with TestKit that applies Jacoco won't brake KTS script fingerprinting"() {
        when:

        // Setting Jacoco destination dir to non-ascii location causes some problems,
        // so let's write to a temporary directory without non-ascii characters
        def jacocoDestinationFile = TextUtil.normaliseFileSeparators("${jacocoDestinationDir.absolutePath}/jacoco.exec")

        settingsFile.delete()
        settingsKotlinFile << """
            rootProject.name = "reproducer"

            dependencyResolutionManagement { 
                ${mavenCentralRepository(GradleDsl.KOTLIN)} 
            }
        """

        buildFile.delete()
        buildKotlinFile << """
            plugins {
                `kotlin-dsl`
                jacoco
                id("com.gradle.plugin-publish") version ("2.0.0")
            }
            
            val jacocoRuntime by configurations.creating
            
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
            
                jacocoRuntime("org.jacoco:org.jacoco.agent:\${JacocoPlugin.DEFAULT_JACOCO_VERSION}:runtime")
            }
            
            gradlePlugin {
                plugins {
                    create("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "com.reproducer.MyPlugin"
                    }
                }
            }
            
            tasks {
                test {
                    useJUnitPlatform()
                }
            }
            
            tasks.withType<Test> {
                val jacoco = the<JacocoTaskExtension>()
                jacoco.setDestinationFile(File("$jacocoDestinationFile"))
            
                systemProperty("jacocoAgentJar", configurations.getByName("jacocoRuntime").singleFile.absolutePath)
                systemProperty("jacocoDestFile", jacoco.destinationFile!!.absolutePath)
            }
        """

        file("src/main/kotlin/com/reproducer/MyPlugin.kt") << """
            package com.reproducer

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            
            class MyPlugin : Plugin<Settings> {
            
                override fun apply(settings: Settings) {
                    println("\${MyPlugin::class.java.name} applied!")
                }
            }
        """

        file("src/test/kotlin/com/reproducer/PluginTest.kt") << """
            package com.reproducer

            import org.gradle.testkit.runner.GradleRunner
            import org.junit.jupiter.api.Assertions.assertTrue
            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.io.TempDir
            import java.io.File

            class PluginTest {

                @TempDir
                lateinit var projectDir: File

                @Test
                fun test() {
                    projectDir.resolve("settings.gradle.kts").writeText(
                        "plugins { id(\\"my-plugin\\") }\\nrootProject.name = \\"test\\""
                    )
                    projectDir.resolve("gradle.properties").writeText(
                        "org.gradle.jvmargs=\\"-javaagent:\${System.getProperty("jacocoAgentJar")}=destfile=\${System.getProperty("jacocoDestFile")}\\""
                    )
                    projectDir.resolve("build.gradle.kts").writeText("")

                    val runner =
                        GradleRunner.create()
                            .withPluginClasspath()
                            .withArguments("help", "--stacktrace")
                            .forwardOutput()
                            .withProjectDir(projectDir)

                    val result = runner.build()

                    assertTrue(result.output.contains("BUILD SUCCESSFUL"))

                }
            }
        """

        then:
        succeeds("test")
    }

}