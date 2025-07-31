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
import org.gradle.api.internal.plugins.BindsSoftwareFeature
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration
import org.gradle.api.internal.plugins.bind
import org.gradle.api.plugins.java.HasSources.JavaSources
import org.gradle.api.plugins.java.JavaClasses
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.language.base.plugins.LifecycleBasePlugin

@BindsSoftwareFeature(CheckstyleSoftwareFeaturePlugin.Binding::class)
class CheckstyleSoftwareFeaturePlugin : Plugin<Project> {
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
    class Binding : SoftwareFeatureBindingRegistration {
        override fun register(builder: SoftwareFeatureBindingBuilder) {
            builder
                .bind<CheckstyleSourceSetDefinition, JavaClasses, CheckstyleModel>("checkstyle") { definition, parent, model ->
                    val checkstyleTask = project.tasks.register("check" + StringUtils.capitalize(parent.name) + "Checkstyle", Checkstyle::class.java) { task ->
                        task.group = LifecycleBasePlugin.VERIFICATION_GROUP
                        task.description = "Runs Checkstyle on the ${parent.name} source set."
                        task.source(parent.javaSources)
                        task.configFile = definition.configFile.asFile.get()
                    }

                    model.reports = checkstyleTask.map { it.reports }
                }
        }
    }

    override fun apply(target: Project) {

    }
}
