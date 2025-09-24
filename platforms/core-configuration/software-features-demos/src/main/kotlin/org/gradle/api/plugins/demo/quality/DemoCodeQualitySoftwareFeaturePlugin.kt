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

package org.gradle.api.plugins.demo.quality

import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.BindsSoftwareFeature
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration
import org.gradle.api.internal.plugins.features.dsl.bindSoftwareFeatureToBuildModel
import org.gradle.api.internal.plugins.features.dsl.bindSoftwareFeatureToDefinition
import org.gradle.api.plugins.java.HasSources
import org.gradle.api.plugins.java.JvmOutputs
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.language.base.plugins.LifecycleBasePlugin

@BindsSoftwareFeature(DemoCodeQualitySoftwareFeaturePlugin.Binding::class)
class DemoCodeQualitySoftwareFeaturePlugin : Plugin<Project> {
    /**
     * javaLibrary {
     *     sources {
     *         javaSources("main") {
     *             demoSourceQuality {
     *             }
     *             demoBytecodeQuality {
     *             }
     *         }
     *     }
     * }
     *
     * groovyLibrary {
     *     sources {
     *         groovySources("main") {
     *             demoSourceQuality {
     *             }
     *             demoBytecodeQuality {
     *             }
     *         }
     *     }
     * }
     */
    class Binding : SoftwareFeatureBindingRegistration {
        override fun register(builder: SoftwareFeatureBindingBuilder) {
            builder.bindSoftwareFeatureToDefinition(
                "demoSourceQuality",
                DemoCodeQualityDefinition::class,
                HasSources.Sources::class
            ) { _, buildModel, target ->
                val codeQualityTask = project.tasks.register("check" + StringUtils.capitalize(target.name) + "DemoSourceQuality", Checkstyle::class.java) { task ->
                    task.group = LifecycleBasePlugin.VERIFICATION_GROUP
                    task.description = "Runs DemoCodeQuality on the ${target.name} source set."
                    task.source(target.sourceDirectories)
                }

                buildModel.reports = codeQualityTask.map { it.reports }
            }

            builder.bindSoftwareFeatureToBuildModel(
                "demoBytecodeQuality",
                DemoCodeQualityDefinition::class,
                JvmOutputs::class
            ) { _, _, target ->
                val targetModel = getBuildModel(target)

                project.tasks.register("check" + StringUtils.capitalize(targetModel.name) + "DemoBytecodeQuality", DefaultTask::class.java) { task ->
                    task.group = LifecycleBasePlugin.VERIFICATION_GROUP
                    task.description = "Runs DemoCodeQuality on ${targetModel.name} resulting bytecode."
                }
            }

        }
    }

    override fun apply(target: Project) = Unit
}
