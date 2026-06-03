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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.kotlin.dsl.KotlinDependencyExtensionsKt

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

class KotlinLanguageVersionPluginCompatibilityIntegrationTest extends AbstractIntegrationSpec {

    private static final String EMBEDDED_KOTLIN_VERSION = KotlinDependencyExtensionsKt.embeddedKotlinVersion

    def "Kotlin plugin built with KGP #maxKgpVersion targeting Kotlin #kotlinLanguageVersion can be applied"() {

        given:
        buildPluginWith(maxKgpVersion, kotlinLanguageVersion)

        when:
        result = applyPlugin()

        then:
        outputContains("My Kotlin plugin applied!")
        outputContains("My task executed!")

        where:
        kotlinLanguageVersion | maxKgpVersion
        "1.8"                 | "2.2.21"
        "1.9"                 | "2.3.21"
        "2.0"                 | EMBEDDED_KOTLIN_VERSION
        "2.1"                 | EMBEDDED_KOTLIN_VERSION
        "2.2"                 | EMBEDDED_KOTLIN_VERSION
    }

    private void buildPluginWith(String maxKgpVersion, String kotlinLanguageVersion) {
        file("plugin/settings.gradle.kts").text = """
            pluginManagement {
                repositories {
                    ${mavenCentralRepositoryDefinition(KOTLIN)}
                }
            }
            rootProject.name = "plugin"
        """
        file("plugin/build.gradle.kts").text = """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget
            import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                kotlin("jvm") version "$maxKgpVersion"
                `java-gradle-plugin`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0"
            ${mavenCentralRepository(KOTLIN)}

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
        file("plugin/src/main/kotlin/com/example/MyPlugin.kt").text = """
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

        executer.inDirectory(file("plugin"))
            .withTasks("publish")
            .noDeprecationChecks() // KGP emits deprecation warnings that vary by version and are not what we test here.
            .run()
    }

    private def applyPlugin() {
        file("consumer/settings.gradle.kts").text = """
            pluginManagement {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                    ${mavenCentralRepositoryDefinition(KOTLIN)}
                }
            }
            rootProject.name = "consumer"
        """
        file("consumer/build.gradle.kts").text = """
            plugins {
                id("my-plugin") version "1.0"
            }
        """

        return executer.inDirectory(file("consumer"))
            .withTasks("myTask")
            .noDeprecationChecks()
            .run()
    }
}
