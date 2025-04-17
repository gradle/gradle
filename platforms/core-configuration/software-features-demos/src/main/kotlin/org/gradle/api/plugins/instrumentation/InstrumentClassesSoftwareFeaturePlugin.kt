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

package org.gradle.api.plugins.instrumentation

import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.plugins.BindsSoftwareFeature
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration
import org.gradle.api.internal.plugins.bind
import org.gradle.api.plugins.java.HasSources.JavaSources
import org.gradle.api.plugins.java.JavaClasses
import org.gradle.language.base.plugins.LifecycleBasePlugin

@BindsSoftwareFeature(InstrumentClassesSoftwareFeaturePlugin.Binding::class)
class InstrumentClassesSoftwareFeaturePlugin : Plugin<Project> {

    /**
     * javaLibrary {
     *     sources {
     *         javaSources("main") {
     *             instrument {
     *             }
     *         }
     *     }
     * }
     */
    class Binding : SoftwareFeatureBindingRegistration {
        override fun register(builder: SoftwareFeatureBindingBuilder) {
            builder
                .bind<InstrumentClassesDefinition, JavaClasses, InstrumentClassesModel>("instrument") { definition, parent, model ->
                    val instrumentClassesTask = project.tasks.register("instrument" + StringUtils.capitalize(parent.name) + "Classes", InstrumentClasses::class.java) { task ->
                        task.group = LifecycleBasePlugin.BUILD_GROUP
                        task.description = "Instruments the ${parent.name} classes."
                        task.bytecodeDir.set(parent.byteCodeDir)
                        task.instrumentedClassesDir.set(definition.destinationDirectory)
                    }

                    model.instrumentedClassesDirectory.set(instrumentClassesTask.map { it.instrumentedClassesDir.get() })
                }
        }
    }

    abstract class InstrumentClasses : DefaultTask() {
        abstract val bytecodeDir: DirectoryProperty
        abstract val instrumentedClassesDir: DirectoryProperty
    }

    override fun apply(target: Project) {

    }
}
