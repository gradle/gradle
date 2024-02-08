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
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class SettingsSchemaAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can access extension registered by included build plugin`() {
        // given: a Settings plugin from an included build
        withDefaultSettingsIn("plugins")
        withBuildScriptIn(
            "plugins",
            """
                plugins { `kotlin-dsl` }
                $repositoriesBlock
            """
        )
        withFile(
            "plugins/src/main/kotlin/my-settings-plugin.settings.gradle.kts",
            """
                interface MySettingsExtension {
                    val myProperty: Property<Int>
                }

                val ext = extensions.create<MySettingsExtension>("mySettingsExtension")
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

                mySettingsExtension {
                    myProperty = 42
                }
            """
        )

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
}
