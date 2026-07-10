/*
 * Copyright 2021 the original author or authors.
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
import org.junit.Test


class GroovyInteroperabilityIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can call GroovyObject methods in withGroovyBuilder`() {
        withDefaultSettings()

        withFile(
            "groovy.gradle",
            """
            class MyExtension {
                String server = 'default'

                def configureServerName(serverName) {
                    this.server = serverName
                }
            }

            class MyPlugin implements Plugin<Project> {
                void apply(project) {
                    project.extensions.add('myextension', MyExtension)
                }
            }

            pluginManager.apply(MyPlugin)
            """
        )

        withBuildScript(
            """
            apply(from = "groovy.gradle")

            extensions["myextension"].withGroovyBuilder {
                getProperty("server")
                setProperty("server", "newValue")
                setMetaClass(getMetaClass())
                invokeMethod("configureServerName", "myservername")
            }
            """
        )

        build()
    }
}
