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

package org.gradle.api.plugins.antlr

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.BindsSoftwareFeature
import org.gradle.api.internal.plugins.SoftwareFeatureBindingBuilder
import org.gradle.api.internal.plugins.SoftwareFeatureBindingRegistration
import org.gradle.api.internal.plugins.features.dsl.bindSoftwareFeatureToBuildModel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.antlr.internal.DefaultAntlrSourceDirectorySet
import org.gradle.api.plugins.java.JavaClasses
import org.gradle.language.base.plugins.LifecycleBasePlugin

@BindsSoftwareFeature(AntlrSoftwareFeaturePlugin.Binding::class)
class AntlrSoftwareFeaturePlugin : Plugin<Project> {
    /**
     * javaLibrary {
     *     sources {
     *         javaSources("main") {
     *             antlr {
     *             }
     *         }
     *     }
     * }
     */
    class Binding : SoftwareFeatureBindingRegistration {
        override fun register(builder: SoftwareFeatureBindingBuilder) {
            builder.bindSoftwareFeatureToBuildModel(
                "antlr",
                AntlrGrammarsDefinition::class,
                JavaClasses::class
            ) { definition, buildModel, target ->
                val parentModel = getOrCreateModel(target)

                definition.grammarSources = createAntlrSourceDirectorySet(definition.name, project.objects)
                val outputDirectory = projectLayout.buildDirectory.dir("/generated-src/antlr/" + definition.grammarSources.getName())

                // Add the generated antlr sources to the java sources
                parentModel.inputSources.srcDir(outputDirectory)

                project.tasks.register("generate" + StringUtils.capitalize(definition.name) + "AntlrSources", AntlrTask::class.java) { antlrTask ->
                    antlrTask.group = LifecycleBasePlugin.BUILD_GROUP
                    antlrTask.description = "Generates sources from the " + definition.grammarSources.name + " Antlr grammars."
                    antlrTask.source = definition.grammarSources
                    antlrTask.outputDirectory = outputDirectory.get().asFile
                }

                buildModel.generatedSourcesDir.set(outputDirectory)
            }
        }

        private fun createAntlrSourceDirectorySet(parentDisplayName: String, objectFactory: ObjectFactory): AntlrSourceDirectorySet {
            val name = "$parentDisplayName.antlr"
            val displayName = "$parentDisplayName Antlr source"
            val antlrSourceSet: AntlrSourceDirectorySet = objectFactory.newInstance(DefaultAntlrSourceDirectorySet::class.java, objectFactory.sourceDirectorySet(name, displayName))
            antlrSourceSet.filter.include("**/*.g")
            antlrSourceSet.filter.include("**/*.g4")
            return antlrSourceSet
        }
    }

    override fun apply(target: Project) = Unit
}
