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
import org.gradle.api.internal.plugins.features.dsl.bindSoftwareFeatureToDefinition
import org.gradle.api.plugins.java.HasJavaSources.JavaSources
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
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
            builder.bindSoftwareFeatureToDefinition(
                "instrument",
                InstrumentClassesDefinition::class,
                JavaSources::class
            ) { definition, buildModel, target ->
                    val instrumentClassesTask = project.tasks.register("instrument" + StringUtils.capitalize(target.name) + "Classes", InstrumentClasses::class.java) { task ->
                        task.group = LifecycleBasePlugin.BUILD_GROUP
                        task.description = "Instruments the ${target.name} classes."
                        task.bytecodeDir.set(getBuildModel(target).byteCodeDir)
                        task.instrumentedClassesDir.set(definition.destinationDirectory)
                    }

                    buildModel.instrumentedClassesDirectory.set(instrumentClassesTask.map { it.instrumentedClassesDir.get() })
                }
        }
    }

    @CacheableTask
    abstract class InstrumentClasses : DefaultTask() {
        @get:InputFiles
        @get:Classpath
        abstract val bytecodeDir: DirectoryProperty

        @get:OutputDirectory
        abstract val instrumentedClassesDir: DirectoryProperty
    }

    override fun apply(target: Project) = Unit
}
