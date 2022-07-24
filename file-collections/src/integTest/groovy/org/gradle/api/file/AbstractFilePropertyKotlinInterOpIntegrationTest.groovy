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

package org.gradle.api.file


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.OutputFile

abstract class AbstractFilePropertyKotlinInterOpIntegrationTest extends AbstractFilePropertyLanguageInterOpIntegrationTest {
    def setup() {
        usesKotlin(pluginDir)
        executer.withStackTraceChecksDisabled()
    }

    abstract void taskDefinition()

    abstract void taskWithNestedBeanDefinition()

    @Override
    void pluginDefinesTask() {
        pluginDir.file("src/main/kotlin/SomePlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("producer", ProducerTask::class.java) {
                        outFile.set(project.layout.buildDirectory.file("intermediate.txt"))
                    }
                }
            }
        """
        taskDefinition()
    }

    @Override
    void pluginDefinesTaskWithNestedBean() {
        pluginDir.file("src/main/kotlin/SomePlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("producer", ProducerTask::class.java) {
                        params.outFile.set(project.layout.buildDirectory.file("intermediate.txt"))
                    }
                }
            }
        """
        pluginDir.file("src/main/kotlin/Params.kt") << """
            import ${RegularFileProperty.name}
            import ${OutputFile.name}

            interface Params {
                @get:OutputFile
                val outFile: RegularFileProperty
            }
        """
        taskWithNestedBeanDefinition()
    }
}
