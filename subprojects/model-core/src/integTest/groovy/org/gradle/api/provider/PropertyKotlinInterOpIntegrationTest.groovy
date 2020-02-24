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
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@LeaksFileHandles
@Requires(TestPrecondition.KOTLIN_SCRIPT)
class PropertyKotlinInterOpIntegrationTest extends AbstractPropertyLanguageInterOpIntegrationTest {
    def setup() {
        usesKotlin(pluginDir)
        executer.withStackTraceChecksDisabled()
        pluginDir.file("src/main/kotlin/SomeTask.kt") << """
            import ${DefaultTask.name}
            import ${Property.name}
            import ${ListProperty.name}
            import ${SetProperty.name}
            import ${MapProperty.name}
            import ${ObjectFactory.name}
            import ${TaskAction.name}
            import ${Inject.name}
            import ${Internal.name}

            open class SomeTask @Inject constructor(objectFactory: ObjectFactory): DefaultTask() {
                @Internal
                val flag = objectFactory.property(Boolean::class.java)
                @Internal
                val message = objectFactory.property(String::class.java)
                @Internal
                val list = objectFactory.listProperty(Int::class.java)
                @Internal
                val set = objectFactory.setProperty(Int::class.java)
                @Internal
                val map = objectFactory.mapProperty(Int::class.java, Boolean::class.java)

                @TaskAction
                fun run() {
                    println("flag = " + flag.get())
                    println("message = " + message.get())
                    println("list = " + list.get())
                    println("set = " + set.get())
                    println("map = " + map.get())
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

    def cleanup() {
        // Let's copy the Kotlin compiler logs in case of failure
        if (result instanceof ExecutionFailure) {
            def pattern = "kotlin-daemon.${new Date().format("yyyy-MM-dd")}.*.log"
            def kotlinCompilerLogFiles = new FileNameFinder().getFileNames(System.getenv("TMPDIR"), pattern)
            def target = buildContext.gradleUserHomeDir.createDir("kotlin-compiler-daemon").toPath()
            kotlinCompilerLogFiles.each {
                def source = Paths.get(it)
                Files.copy(source, target.resolve(source.fileName), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
