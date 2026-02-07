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

package org.gradle.api.plugins.checkstyle

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.BindsProjectFeature
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilder
import org.gradle.api.internal.plugins.ProjectFeatureBinding
import org.gradle.api.internal.plugins.features.dsl.bindProjectFeatureToDefinition
import org.gradle.api.plugins.java.HasJavaSources
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.features.registration.TaskRegistrar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject

@BindsProjectFeature(CheckstyleProjectFeaturePlugin.Binding::class)
class CheckstyleProjectFeaturePlugin : Plugin<Project> {
    /**
     * javaLibrary {
     *     sources {
     *         javaSources("main") {
     *             checkstyle {
     *             }
     *         }
     *     }
     * }
     */
    class Binding : ProjectFeatureBinding {
        override fun bind(builder: ProjectFeatureBindingBuilder) {
            builder.bindProjectFeatureToDefinition(
                "checkstyle",
                CheckstyleSourceSetDefinition::class,
                HasJavaSources.JavaSources::class
            ) { definition, buildModel, target ->
                val services = objectFactory.newInstance(Services::class.java)
                val checkstyleTask = services.taskRegistrar.register("check" + StringUtils.capitalize(target.name) + "Checkstyle", Checkstyle::class.java) { task ->
                    task.group = LifecycleBasePlugin.VERIFICATION_GROUP
                    task.description = "Runs Checkstyle on the ${target.name} source set."
                    task.source(getBuildModel(target).inputSources)
                    task.configFile = definition.configFile.asFile.get()
                }

                buildModel.reports = checkstyleTask.map { it.reports }
            }
        }

        interface Services {
            @get:Inject
            val taskRegistrar: TaskRegistrar
        }
    }

    override fun apply(target: Project) = Unit
}
