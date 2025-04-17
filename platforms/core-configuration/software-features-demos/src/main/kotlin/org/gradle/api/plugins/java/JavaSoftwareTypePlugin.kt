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

package org.gradle.api.plugins.java

import org.apache.commons.lang3.StringUtils.capitalize
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.BindsSoftwareType
import org.gradle.api.internal.plugins.SoftwareTypeBindingBuilder
import org.gradle.api.internal.plugins.SoftwareTypeBindingRegistration
import org.gradle.api.internal.plugins.bind
import org.gradle.api.plugins.internal.java.DefaultJavaSoftwareType
import org.gradle.api.plugins.java.JavaClasses.DefaultJavaClasses
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
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
            builder
                .bind<JavaSoftwareType, JavaLibraryModel>("javaLibrary") { definition, model ->
                    definition.sources.register("main")
                    definition.sources.register("test")

                    definition.sources.all { javaSources ->
                        // Should be TaskRegistrar with some sort of an implicit namer for the context
                        val compileTask = project.tasks.register(
                            "compile" + capitalize(javaSources.name) + "Java",
                            JavaCompile::class.java
                        ) { task ->
                            task.group = LifecycleBasePlugin.BUILD_GROUP
                            task.description = "Compiles the " + javaSources.name + " Java source."
                            task.source(javaSources.java.asFileTree)
                        }

                        val processResourcesTask = project.tasks.register("process" + capitalize(javaSources.name) + "Resources", Copy::class.java) { task ->
                            task.group = LifecycleBasePlugin.BUILD_GROUP
                            task.description = "Processes the " + javaSources.name + " resources."
                            task.from(javaSources.resources.asFileTree)
                        }

                        // Creates an extension on javaSources containing its classes object
                        val classes = getOrCreateModel(javaSources, DefaultJavaClasses::class.java)
                        classes.name = javaSources.name
                        classes.javaSources.source(javaSources.java)
                        classes.byteCodeDir.set(compileTask.map { it.destinationDirectory.get() })
                        classes.processedResourcesDir.fileProvider(processResourcesTask.map { it.destinationDir })
                        model.classes.add(classes)
                    }

                    val mainClasses = model.classes.named("main")
                    val jarTask = project.tasks.register("jar", Jar::class.java) { task ->
                        task.from(mainClasses.map { it.byteCodeDir })
                        task.from(mainClasses.map { it.processedResourcesDir })
                    }

                    model.jarFile.set(jarTask.map { it.archiveFile.get() })
                }
                .withDslImplementationType(DefaultJavaSoftwareType::class.java)
                .withNestedBinding(HasSources.JavaSources::class.java, JavaClasses::class.java)
        }
    }

    override fun apply(target: Project) {

    }
}
