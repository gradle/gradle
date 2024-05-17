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

package org.gradle.api.provider


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.test.fixtures.file.LeaksFileHandles

@LeaksFileHandles
abstract class AbstractPropertyKotlinInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
    def setup() {
        usesKotlin(pluginDir)
        executer.withStackTraceChecksDisabled()
    }

    @Override
    void pluginSetsValues() {
        pluginDir.file("src/main/kotlin/SomePlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("someTask", SomeTask::class.java) {
                        flag.set(true)
                        message.set("some value")
                        number.set(1.23)
                        list.set(listOf(1, 2))
                        set.set(listOf(1, 2))
                        map.set(mapOf(1 to true, 2 to false))
                    }
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValuesUsingCallable() {
        pluginDir.file("src/main/kotlin/SomePlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("someTask", SomeTask::class.java) {
                        flag.set(project.provider { true })
                        message.set(project.provider { "some value" })
                        number.set(project.provider { 1.23 })
                        list.set(project.provider { listOf(1, 2) })
                        set.set(project.provider { listOf(1, 2) })
                        map.set(project.provider { mapOf(1 to true, 2 to false) })
                    }
                }
            }
        """
    }

    @Override
    void pluginSetsCalculatedValuesUsingMappedProvider() {
        pluginDir.file("src/main/kotlin/SomePlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("someTask", SomeTask::class.java) {
                        val provider = project.provider { "some value" }
                        flag.set(provider.map { s -> !s.isEmpty() })
                        message.set(provider.map { s -> s })
                        number.set(provider.map { s -> 1.23 })
                        list.set(provider.map { s -> listOf(1, 2) })
                        set.set(provider.map { s -> listOf(1, 2) })
                        map.set(provider.map { s -> mapOf(1 to true, 2 to false) })
                    }
                }
            }
        """
    }

    @Override
    void pluginDefinesTask() {
        pluginDir.file("src/main/kotlin/SomePlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("someTask", SomeTask::class.java)
                }
            }
        """
    }
}
