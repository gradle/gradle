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
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.dsl.bindProjectType
import org.gradle.api.plugins.internal.java.DefaultJavaProjectType
import org.gradle.api.plugins.java.JavaClasses
import org.gradle.api.plugins.java.JavaClasses.DefaultJavaClasses
import org.gradle.api.plugins.java.JavaLibraryModel
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
            builder.bindProjectType("javaLibrary", ApplyAction::class)
                .withUnsafeDefinitionImplementationType(DefaultJavaProjectType::class.java)
                .withNestedBuildModelImplementationType(JavaClasses::class.java, DefaultJavaClasses::class.java)
                .withUnsafeApplyAction()
        }

        abstract class ApplyAction : ProjectTypeApplyAction<JavaProjectType, JavaLibraryModel> {
            @get:Inject
            abstract val taskRegistrar: TaskRegistrar

            override fun apply(
                context: ProjectFeatureApplicationContext,
                definition: JavaProjectType,
                buildModel: JavaLibraryModel
            ) {
                definition.sources.all { source ->
                    // Should be TaskRegistrar with some sort of an implicit namer for the context
                    val compileTask = taskRegistrar.register(
                        "compile" + capitalize(source.name) + "Java",
                        JavaCompile::class.java
                    ) { task ->
                        task.group = LifecycleBasePlugin.BUILD_GROUP
                        task.description = "Compiles the " + source.name + " Java source."
                        task.source(source.sourceDirectories.asFileTree)
                    }

                    val processResourcesTask = context.registerResourcesProcessing(source, taskRegistrar)

                    // Creates an extension on javaSources containing its classes object
                    buildModel.classes.add(context.getBuildModel(source).apply {
                        name = source.name
                        inputSources.source(source.sourceDirectories)
                        byteCodeDir.set(compileTask.map { it.destinationDirectory.get() })
                        processedResourcesDir.fileProvider(processResourcesTask.map { it.destinationDir })
                    })
                }

                val mainClasses = buildModel.classes.named("main")
                context.registerJar(mainClasses, buildModel, taskRegistrar)
            }
        }
    }

    override fun apply(target: Project) = Unit
}

