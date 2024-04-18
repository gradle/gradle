/*
 * Copyright 2024 the original author or authors.
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
import org.junit.Before
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class SettingsSchemaAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Before
    fun withSettingsPluginFromIncludedBuild() {
        // given: a Settings plugin from an included build
        withDefaultSettingsIn("plugins")
        withBuildScriptIn(
            "plugins",
            """
                plugins { `kotlin-dsl` }
                $repositoriesBlock
            """
        )
        withPluginSourceFile(
            "my/MySettingsExtension.kt",
            """
                package my
                import org.gradle.api.provider.*
                interface MySettingsExtension {
                    val myProperty: Property<Int>
                }
            """
        )
        withPluginSourceFile(
            "my-settings-plugin.settings.gradle.kts",
            """
                val ext = extensions.create<my.MySettingsExtension>("mySettingsExtension")
                gradle.rootProject {
                    tasks.register("ok") {
                        val myProperty = ext.myProperty
                        doLast { println("It's ${'$'}{myProperty.get()}!") }
                    }
                }
            """
        )
        // and: a settings script that uses the plugin
        withDefaultSettings().appendText(
            """
                pluginManagement {
                    includeBuild("plugins")
                }

                plugins {
                    id("my-settings-plugin")
                }

                // accessor function
                mySettingsExtension {
                    myProperty = 42
                }

                // accessor property
                println(mySettingsExtension.myProperty)
            """
        )
    }

    private
    fun withPluginSourceFile(fileName: String, text: String) {
        withFile("plugins/src/main/kotlin/$fileName", text)
    }

    @Test
    fun `can access extension registered by included build plugin`() {
        // when:
        val result = build("ok")

        // then:
        result.assertOutputContains("It's 42!")

        // when: plugin changes in an incompatible way
        withFile("plugins/src/main/kotlin/my-settings-plugin.settings.gradle.kts", "")

        // then:
        buildAndFail("ok").apply {
            hasErrorOutput("Unresolved reference: mySettingsExtension")
        }
    }

    @Test
    fun `can access extension registered by included build plugin via custom accessor`() {
        // when:
        withPluginSourceFile(
            "org/gradle/kotlin/dsl/MySettingsAccessor.kt",
            """
                package org.gradle.kotlin.dsl

                import org.gradle.api.initialization.*

                fun Settings.mySettingsExtension(action: my.MySettingsExtension.() -> Unit) {
                    println("Plugin extension takes precedence!")
                    configure<my.MySettingsExtension> {
                        action()
                    }
                }
            """
        )

        val result = build("ok")

        // then:
        result.assertOutputContains("It's 42!")
        result.assertOutputContains("Plugin extension takes precedence!")
    }

    @Test
    fun `can access extension registered by included build plugin via custom Action-based accessor`() {
        // when:
        withPluginSourceFile(
            "org/gradle/kotlin/dsl/MySettingsAccessor.kt",
            """
                package org.gradle.kotlin.dsl

                import org.gradle.api.initialization.*

                fun Settings.mySettingsExtension(action: org.gradle.api.Action<my.MySettingsExtension>) {
                    println("Plugin extension takes precedence!")
                    configure<my.MySettingsExtension> {
                        action(this)
                    }
                }
            """
        )

        val result = build("ok")

        // then:
        result.assertOutputContains("It's 42!")
        result.assertOutputContains("Plugin extension takes precedence!")
    }
}
