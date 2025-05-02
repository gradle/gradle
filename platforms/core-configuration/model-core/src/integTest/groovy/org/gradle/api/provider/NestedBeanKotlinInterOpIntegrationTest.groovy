/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

class NestedBeanKotlinInterOpIntegrationTest extends AbstractNestedBeanLanguageInterOpIntegrationTest {
    def setup() {
        usesKotlin(pluginDir)
        executer.withStackTraceChecksDisabled()
        pluginDir.file("src/main/kotlin/Params.kt") << """
            import ${Property.name}
            import ${Internal.name}

            interface Params {
                @get:Internal
                val flag: Property<Boolean>
            }
        """
        pluginDir.file("src/main/kotlin/SomeTask.kt") << """
            import ${DefaultTask.name}
            import ${TaskAction.name}
            import ${Nested.name}

            abstract class SomeTask: DefaultTask() {
                @get:Nested
                abstract val params: Params

                @TaskAction
                fun run() {
                    println("flag = " + params.flag.get())
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
