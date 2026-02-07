/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.plugins.java.plugin

import org.apache.commons.lang3.StringUtils.capitalize
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.plugins.ProjectTypeBinding
import org.gradle.api.internal.plugins.features.dsl.bindProjectType
import org.gradle.api.plugins.internal.java.DefaultJavaProjectType
import org.gradle.api.plugins.java.JavaClasses.DefaultJavaClasses
import org.gradle.api.plugins.java.JavaProjectType
import org.gradle.features.registration.TaskRegistrar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject

@BindsProjectType(JavaProjectTypePlugin.Binding::class)
class JavaProjectTypePlugin : Plugin<Project> {
    /**
     * javaLibrary {
     *     version = "11"
     *     sources {
     *        javaSources("main") {
     *        }
     *     }
     * }
     */
    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("javaLibrary") { definition: JavaProjectType, model ->
                val services = objectFactory.newInstance(Services::class.java)
                definition.sources.register("main")
                definition.sources.register("test")

                definition.sources.all { source ->
                    // Should be TaskRegistrar with some sort of an implicit namer for the context
                    val compileTask = services.taskRegistrar.register(
                        "compile" + capitalize(source.name) + "Java",
                        JavaCompile::class.java
                    ) { task ->
                        task.group = LifecycleBasePlugin.BUILD_GROUP
                        task.description = "Compiles the " + source.name + " Java source."
                        task.source(source.sourceDirectories.asFileTree)
                    }

                    val processResourcesTask = registerResourcesProcessing(source, services.taskRegistrar)

                    // Creates an extension on javaSources containing its classes object
                    model.classes.add(registerBuildModel(source, DefaultJavaClasses::class.java).apply {
                        name = source.name
                        inputSources.source(source.sourceDirectories)
                        byteCodeDir.set(compileTask.map { it.destinationDirectory.get() })
                        processedResourcesDir.fileProvider(processResourcesTask.map { it.destinationDir })
                    })
                }

                val mainClasses = model.classes.named("main")
                registerJar(mainClasses, model, services.taskRegistrar)
            }
            .withUnsafeDefinitionImplementationType(DefaultJavaProjectType::class.java)
        }

        interface Services {
            @get:Inject
            val taskRegistrar: TaskRegistrar
        }
    }

    override fun apply(target: Project) = Unit
}

