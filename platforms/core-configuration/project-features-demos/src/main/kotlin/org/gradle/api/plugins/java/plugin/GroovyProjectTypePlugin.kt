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
import org.gradle.api.internal.plugins.ProjectTypeBindingRegistration
import org.gradle.api.internal.plugins.features.dsl.bindProjectType
import org.gradle.api.plugins.internal.java.DefaultGroovyProjectType
import org.gradle.api.plugins.java.GroovyClasses
import org.gradle.api.plugins.java.GroovyProjectType
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.language.base.plugins.LifecycleBasePlugin

@BindsProjectType(GroovyProjectTypePlugin.Binding::class)
class GroovyProjectTypePlugin : Plugin<Project> {
    /**
     * groovyLibrary {
     *     version = "11"
     *     sources {
     *        groovySources("main") {
     *        }
     *     }
     * }
     */
    class Binding : ProjectTypeBindingRegistration {
        override fun register(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("groovyLibrary") { definition: GroovyProjectType, model ->
                definition.sources.register("main")
                definition.sources.register("test")

                definition.sources.all { source ->
                    val compileTask = project.tasks.register(
                        "compile" + capitalize(source.name) + "Groovy",
                        GroovyCompile::class.java
                    ) { task ->
                        task.group = LifecycleBasePlugin.BUILD_GROUP
                        task.description = "Compiles the " + source.name + " Groovy source."
                        task.source(source.sourceDirectories.asFileTree)
                    }

                    val processResourcesTask = registerResourcesProcessing(source)

                    model.classes.add(registerBuildModel(source, GroovyClasses.DefaultGroovyClasses::class.java).apply {
                        name = source.name
                        inputSources.source(source.sourceDirectories)
                        byteCodeDir.set(compileTask.map { it.destinationDirectory.get() })
                        processedResourcesDir.fileProvider(processResourcesTask.map { it.destinationDir })
                    })
                }

                registerJar(model.classes.named("main"), model)
            }
            .withDefinitionImplementationType(DefaultGroovyProjectType::class.java)
        }
    }

    override fun apply(target: Project) = Unit
}
