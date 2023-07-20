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

package org.gradle.kotlin.dsl.plugins

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.util.GradleVersion

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue

@TargetVersions("5.0+")
class PrecompiledKotlinPluginCrossVersionSpec extends CrossVersionIntegrationSpec {

    private static final GradleVersion GRADLE_7_1 = GradleVersion.version("7.1")
    private static final GradleVersion GRADLE_5_3 = GradleVersion.version("5.3")

    boolean supportsSettingsPluginsBlock
    boolean supportsProjectAccessors

    def setup() {
        supportsSettingsPluginsBlock = previous.version >= GRADLE_7_1 && current.version >= GRADLE_7_1
        supportsProjectAccessors = previous.version >= GRADLE_5_3 && current.version >= GRADLE_5_3
    }

    def "precompiled Kotlin plugins can be used with current Gradle version when built with Gradle 5.0+"() {
        assumeTrue(previous.version >= GradleVersion.version('5.0'))

        // See https://github.com/gradle/gradle/issues/24754
        assumeFalse("broken", previous.version == GradleVersion.version("8.1"))

        given:
        precompiledKotlinPluginsBuiltWith(previous)

        when:
        def result = pluginsAppliedWith(current)

        then:
        result.assertOutputContains("My gradle plugin applied!")
        result.assertOutputContains("My settings plugin applied!")
        result.assertOutputContains("settings pluginManagement {}")
        if (supportsSettingsPluginsBlock) {
            result.assertOutputContains("settings plugins {}")
        }
        result.assertOutputContains("My project plugin applied!")
        result.assertOutputContains("My task executed!")
    }

    def "precompiled Kotlin plugins built with current Gradle version can be used with Gradle 6.0+"() {
        assumeTrue(previous.version >= GradleVersion.version('6.0'))

        given:
        precompiledKotlinPluginsBuiltWith(current)

        when:
        def result = pluginsAppliedWith(previous)

        then:
        result.assertOutputContains("My gradle plugin applied!")
        result.assertOutputContains("My settings plugin applied!")
        result.assertOutputContains("settings pluginManagement {}")
        if (supportsSettingsPluginsBlock) {
            result.assertOutputContains("settings plugins {}")
        }
        result.assertOutputContains("My project plugin applied!")
        result.assertOutputContains("My task executed!")
    }

    private void precompiledKotlinPluginsBuiltWith(GradleDistribution distribution) {

        file("plugin/settings.gradle.kts").text = ""
        file("plugin/build.gradle.kts").text = """
            plugins {
                `kotlin-dsl`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0"
            ${mavenCentralRepository(KOTLIN)}
            publishing {
                repositories {
                    maven { url = uri("${mavenRepo.uri}") }
                }
            }
        """
        file("plugin/src/main/kotlin/my-gradle-plugin.init.gradle.kts").text = """
            println("My gradle plugin applied!")
        """
        file("plugin/src/main/kotlin/my-settings-plugin.settings.gradle.kts").text = """
            pluginManagement {
                println("settings pluginManagement {}")
            }
            ${supportsSettingsPluginsBlock ? """
            plugins {
                println("settings plugins {}")
            }
            """ : ""}
            println("My settings plugin applied!")
        """
        file("plugin/src/main/kotlin/my-project-plugin.gradle.kts").text = """
            plugins {
                id("base")
            }
            println("My project plugin applied!")
            val myTask = tasks.register("myTask") {
                doLast {
                    println("My task executed!")
                }
            }
            ${supportsProjectAccessors ? """
            tasks.assemble {
                dependsOn(myTask)
            }
            """ : ""}
        """
        version(distribution)
            .inDirectory(file("plugin"))
            .withTasks("publish")
            .run()
    }

    private ExecutionResult pluginsAppliedWith(GradleDistribution distribution) {
        file("consumer/init.gradle.kts").text = """
            initscript {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
                dependencies {
                    classpath("com.example:plugin:1.0")
                }
            }
            apply<MyGradlePluginPlugin>()
        """
        file("consumer/settings.gradle.kts").text = """
            pluginManagement {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
            }
            ${supportsSettingsPluginsBlock ? """
            plugins {
                id("my-settings-plugin") version "1.0"
            }
            """ : """
            buildscript {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
                dependencies {
                    classpath("com.example:plugin:1.0")
                }
            }
            apply<MySettingsPluginPlugin>()
            """}
        """
        file("consumer/build.gradle.kts").text = """
            plugins {
                id("my-project-plugin")
            }
        """
        return version(distribution)
            .inDirectory(file("consumer"))
            .withArgument("-I")
            .withArgument("init.gradle.kts")
            .withTasks("myTask")
            .run()
    }
}
