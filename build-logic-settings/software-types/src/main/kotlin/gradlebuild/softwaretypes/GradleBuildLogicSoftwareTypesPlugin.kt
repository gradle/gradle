/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.softwaretypes

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectTypeBinding
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.plugins.software.RegistersProjectFeatures
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

@RegistersProjectFeatures(KotlinBuildLogicProjectTypePlugin::class)
open class GradleBuildLogicSoftwareTypesPlugin : Plugin<Settings> {
    override fun apply(target: Settings) = Unit
}

@BindsProjectType(KotlinBuildLogicProjectTypePlugin.Binding::class)
open class KotlinBuildLogicProjectTypePlugin : Plugin<Project> {

    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("kotlinBuildLogic", KotlinBuildLogicDefinition::class.java) { context, definition, model ->
                context.project.run {
                    plugins.apply("org.gradle.kotlin.kotlin-dsl")
                    group = "gradlebuild"
                    afterEvaluate {
                        description = definition.description.get()
                    }
                    configurations.getByName("compileOnly").dependencies.addAllLater(
                        definition.dependencies.compileOnly.dependencies
                    )
                    configurations.getByName("implementation").dependencies.addAllLater(
                        definition.dependencies.implementation.dependencies
                    )
                    configurations.getByName("api").dependencies.addAllLater(
                        definition.dependencies.api.dependencies
                    )
                }
            }.withUnsafeDefinition()
        }
    }

    override fun apply(target: Project) = Unit
}

interface KotlinBuildLogicDefinition : Definition<BuildModel.None> {

    val description: Property<String>

    @get:Nested
    val dependencies: BuildLogicDependencies
}

interface BuildLogicDependencies : Dependencies {

    val compileOnly: DependencyCollector
    val implementation: DependencyCollector
    val api: DependencyCollector

    fun catalog(notation: String): ExternalModuleDependency =
        notation.split('.').let { parts ->
            check(parts.size == 2) { "$notation must be a dot separated name" }
            project.extensions.getByType<VersionCatalogsExtension>()
                .find(parts[0])
                .flatMap { it.findLibrary(parts[1]) }
                .orElseThrow()
                .get()
        }

    fun platformProject(projectPath: String): Dependency =
        project.dependencies.platform(project(projectPath))

    fun kotlinDlsGradlePlugin(): String =
        "org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:$expectedKotlinDslPluginsVersion"
}

