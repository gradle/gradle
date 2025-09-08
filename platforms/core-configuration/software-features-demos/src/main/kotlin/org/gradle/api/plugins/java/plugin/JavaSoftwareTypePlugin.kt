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
import org.gradle.api.internal.plugins.BindsSoftwareType
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder
import org.gradle.api.internal.plugins.SoftwareTypeBindingRegistration
import org.gradle.api.internal.plugins.features.dsl.bindSoftwareType
import org.gradle.api.plugins.internal.java.DefaultJavaSoftwareType
import org.gradle.api.plugins.java.JavaClasses.DefaultJavaClasses
import org.gradle.api.plugins.java.JavaSoftwareType
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.base.plugins.LifecycleBasePlugin

@BindsSoftwareType(JavaSoftwareTypePlugin.Binding::class)
class JavaSoftwareTypePlugin : Plugin<Project> {
    /**
     * javaLibrary {
     *     version = "11"
     *     sources {
     *        javaSources("main") {
     *        }
     *     }
     * }
     */
    class Binding : SoftwareTypeBindingRegistration {
        override fun register(builder: SoftwareTypeBindingBuilder) {
            builder.bindSoftwareType("javaLibrary") { definition: JavaSoftwareType, model ->
                definition.sources.register("main")
                definition.sources.register("test")

                definition.sources.all { source ->
                    // Should be TaskRegistrar with some sort of an implicit namer for the context
                    val compileTask = project.tasks.register(
                        "compile" + capitalize(source.name) + "Java",
                        JavaCompile::class.java
                    ) { task ->
                        task.group = LifecycleBasePlugin.BUILD_GROUP
                        task.description = "Compiles the " + source.name + " Java source."
                        task.source(source.sourceDirectories.asFileTree)
                    }

                    val processResourcesTask = registerResourcesProcessing(source)

                    // Creates an extension on javaSources containing its classes object
                    model.classes.add(getOrCreateModel(source, DefaultJavaClasses::class.java).apply {
                        name = source.name
                        inputSources.source(source.sourceDirectories)
                        byteCodeDir.set(compileTask.map { it.destinationDirectory.get() })
                        processedResourcesDir.fileProvider(processResourcesTask.map { it.destinationDir })
                    })
                }

                val mainClasses = model.classes.named("main")
                registerJar(mainClasses, model)
            }
            .withDefinitionImplementationType(DefaultJavaSoftwareType::class.java)
        }
    }

    override fun apply(target: Project) = Unit
}

