/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.test.fixtures.file.LeaksFileHandles

import org.junit.Test

import java.io.File


class KotlinOneDotOnePluginIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @LeaksFileHandles("Kotlin Gradle Plugin 1.1")
    fun `given a plugin compiled against Kotlin one dot one, it will run against the embedded Kotlin version`() {

        assumeJavaLessThan9()

        withBuildScript("""
            buildscript {
                repositories {
                    ivy(url = "${publishedKotlinOneDotOnePlugin().toURI()}")
                    jcenter()
                }
                dependencies {
                    classpath("org.gradle.kotlin.dsl.fixtures:plugin-compiled-against-kotlin-1.1:1.0")
                }
            }

            apply<fixtures.ThePlugin>()

            tasks.withType<fixtures.ThePluginTask> {
                from = "new value"
                doLast {
                    println(transform { "*[" + it + "]*" })
                }
            }
        """)

        assert(
            build("the-plugin-task").output.contains("*[new value]*"))
    }

    private
    fun publishedKotlinOneDotOnePlugin(): File {

        val pluginDir = newDir("plugin-compiled-against-kotlin-1.1")
        val pluginRepositoryDir = newDir("plugin-repository")

        withDefaultSettingsIn(pluginDir.name)
        withFile("${pluginDir.name}/build.gradle", """
            plugins {
                id 'nebula.kotlin' version '1.1.0'
            }

            group 'org.gradle.kotlin.dsl.fixtures'

            version '1.0'

            tasks.named("uploadArchives").configure {
                repositories {
                    ivy { url '../${pluginRepositoryDir.name}' }
                }
            }

            dependencies {
                compile(gradleApi())
            }

            repositories {
                jcenter()
            }

            defaultTasks 'uploadArchives'

            tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
                kotlinOptions.jvmTarget = "1.8"
            }
        """)
        withFile("${pluginDir.name}/src/main/kotlin/fixtures/ThePlugin.kt", """
            package fixtures

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class ThePlugin : Plugin<Project> {

                override fun apply(target: Project) {
                    target.tasks.create("the-plugin-task", ThePluginTask::class.java)
                }
            }

            open class ThePluginTask : DefaultTask() {

                var from: String = "default from value"

                open fun transform(f: (String) -> String) = f(from)

                @TaskAction
                fun run() {
                    println(transform { "it = ${'$'}it" })
                }
            }
        """)

        executer.inDirectory(pluginDir)
            .withArguments("uploadArchives")
            .expectDeprecationWarning()
            .run()

        return pluginRepositoryDir
    }
}
