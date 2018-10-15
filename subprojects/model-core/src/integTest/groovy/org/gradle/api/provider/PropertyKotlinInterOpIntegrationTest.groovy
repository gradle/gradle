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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import javax.inject.Inject

@Requires(TestPrecondition.KOTLIN_SCRIPT)
class PropertyKotlinInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
    def setup() {
        usesKotlin(pluginDir)
        pluginDir.file("src/main/kotlin/SomeTask.kt") << """
            import ${DefaultTask.name}
            import ${Property.name}
            import ${ObjectFactory.name}
            import ${TaskAction.name}
            import ${Inject.name}

            open class SomeTask @Inject constructor(objectFactory: ObjectFactory): DefaultTask() {
                val flag = objectFactory.property(Boolean::class.java)
                val message = objectFactory.property(String::class.java)
                
                @TaskAction
                fun run() {
                    println("flag = " + flag.get())
                    println("message = " + message.get())
                }
            }
        """
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
                        message.set(provider. map { s -> s })
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
