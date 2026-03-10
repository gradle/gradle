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

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.GradleDependencies
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.annotations.RegistersProjectFeatures
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import javax.inject.Inject

@RegistersProjectFeatures(
    KotlinBuildLogicProjectTypePlugin::class,
    KotlinDslProjectTypePlugin::class,
    GroovyKotlinDslProjectTypePlugin::class,
    JavaPlatformBuildLogicProjectTypePlugin::class,
    JavaLibraryBuildLogicProjectTypePlugin::class
)
open class GradleBuildLogicSoftwareTypesPlugin : Plugin<Settings> {
    override fun apply(target: Settings) = Unit
}

abstract class BaseGradleBuilProjectTypePlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit

    interface Services {
        @get:Inject
        val project: Project
    }
}

@BindsProjectType(KotlinBuildLogicProjectTypePlugin.Binding::class)
open class KotlinBuildLogicProjectTypePlugin : BaseGradleBuilProjectTypePlugin() {

    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("kotlinBuildLogic", JavaBuildLogicDefinition::class.java) { context, definition, model ->
                context.objectFactory.newInstance<Services>().project.run {
                    plugins.apply("org.gradle.kotlin.kotlin-dsl")
                    group = "gradlebuild"
                    afterEvaluate {
                        description = definition.description.get()
                    }
                    for ((scope, collector) in definition.dependencies.scopeToCollector()) {
                        configurations.getByName(scope).dependencies.addAllLater(collector.dependencies)
                    }
                    tasks.getByName("test", Test::class) {
                        useJUnitPlatform()
                    }
                }
            }.withUnsafeDefinition().withUnsafeApplyAction()
        }
    }
}

@BindsProjectType(JavaLibraryBuildLogicProjectTypePlugin.Binding::class)
open class JavaLibraryBuildLogicProjectTypePlugin : BaseGradleBuilProjectTypePlugin() {

    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("javaLibraryBuildLogic", JavaBuildLogicDefinition::class.java) { context, definition, model ->
                context.objectFactory.newInstance<Services>().project.run {
                    repositories.mavenCentral()
                    plugins.apply("java-library")
                    group = "gradlebuild"
                    afterEvaluate {
                        description = definition.description.get()
                    }
                    for ((scope, collector) in definition.dependencies.scopeToCollector()) {
                        configurations.getByName(scope).dependencies.addAllLater(collector.dependencies)
                    }
                }
            }.withUnsafeDefinition().withUnsafeApplyAction()
        }
    }
}

@BindsProjectType(JavaPlatformBuildLogicProjectTypePlugin.Binding::class)
open class JavaPlatformBuildLogicProjectTypePlugin : BaseGradleBuilProjectTypePlugin() {

    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("javaPlatformBuildLogic", BuildLogicDefinition::class.java) { context, definition, model ->
                context.objectFactory.newInstance<Services>().project.run {
                    plugins.apply("java-platform")
                    group = "gradlebuild"

                    val kotlinVersion = providers.gradleProperty("buildKotlinVersion").getOrElse(embeddedKotlinVersion)

                    val distributionDependencies = project.extensions.getByType<VersionCatalogsExtension>().named("buildLibs")
                    distributionDependencies.libraryAliases.forEach { alias ->
                        val constr = distributionDependencies.findLibrary(alias).get().map { module ->
                            if (module.group == "org.jetbrains.kotlin") {
                                module.copy().apply {
                                    version {
                                        strictly(kotlinVersion)
                                    }
                                }
                            } else {
                                module
                            }
                        }
                        dependencies.constraints.add("api", constr)
                    }
                }
            }.withUnsafeDefinition().withUnsafeApplyAction()
        }
    }
}

@BindsProjectType(KotlinDslProjectTypePlugin.Binding::class)
open class KotlinDslProjectTypePlugin : BaseGradleBuilProjectTypePlugin() {

    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("kotlinDslPlugin", KotlinDslDefinition::class.java) { context, definition, model ->
                context.objectFactory.newInstance<Services>().project.run {
                    plugins.apply("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
                    group = "gradlebuild"
                    afterEvaluate {
                        description = definition.description.get()

                        extensions.configure<GradlePluginDevelopmentExtension>("gradlePlugin") {
                            definition.gradlePlugins.forEach {
                                plugins {
                                    register(it.name) {
                                        id = it.id.get()
                                        implementationClass = it.implementationClass.get()
                                    }
                                }
                            }
                        }
                    }
                    for ((scope, collector) in definition.dependencies.scopeToCollector()) {
                        configurations.getByName(scope).dependencies.addAllLater(collector.dependencies)
                    }
                }
            }.withUnsafeDefinition().withUnsafeApplyAction()
        }
    }
}

@BindsProjectType(GroovyKotlinDslProjectTypePlugin.Binding::class)
open class GroovyKotlinDslProjectTypePlugin : BaseGradleBuilProjectTypePlugin() {

    class Binding : ProjectTypeBinding {
        override fun bind(builder: ProjectTypeBindingBuilder) {
            builder.bindProjectType("groovyKotlinDslPlugin", JavaBuildLogicDefinition::class.java) { context, definition, model ->
                context.objectFactory.newInstance<Services>().project.run {
                    plugins.apply("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
                    plugins.apply("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
                    group = "gradlebuild"
                    afterEvaluate {
                        description = definition.description.get()
                    }
                    for ((scope, collector) in definition.dependencies.scopeToCollector()) {
                        configurations.getByName(scope).dependencies.addAllLater(collector.dependencies)
                    }
                    tasks.named("compileGroovy", GroovyCompile::class) {
                        classpath += files(tasks.named("compileKotlin"))
                    }
                }
            }.withUnsafeDefinition().withUnsafeApplyAction()
        }
    }
}

interface BuildLogicDefinition : Definition<BuildModel.None> {
    val description: Property<String>
}

interface JavaBuildLogicDefinition : BuildLogicDefinition {

    @get:Nested
    val dependencies: BuildLogicDependencies
}

interface KotlinDslDefinition : JavaBuildLogicDefinition {
    val gradlePlugins: NamedDomainObjectContainer<GradlePlugin>
}

interface GradlePlugin : Named {
    val id: Property<String>
    val implementationClass: Property<String>
}

interface BuildLogicDependencies : GradleDependencies, PlatformDependencyModifiers {

    val implementation: DependencyCollector
    val api: DependencyCollector
    val compileOnly: DependencyCollector
    val testImplementation: DependencyCollector
    val testRuntimeOnly: DependencyCollector

    @HiddenInDefinition
    fun scopeToCollector(): Map<String, DependencyCollector> =
        mapOf(
            "api" to api,
            "implementation" to implementation,
            "compileOnly" to compileOnly,
            "testImplementation" to testImplementation,
            "testRuntimeOnly" to testRuntimeOnly
        )

    fun catalog(notation: String): ExternalModuleDependency =
        notation.split('.').let { parts ->
            check(parts.size == 2) { "$notation must be a dot separated name" }
            targetProject.extensions.getByType<VersionCatalogsExtension>()
                .find(parts[0])
                .flatMap { it.findLibrary(parts[1]) }
                .orElseThrow()
                .get()
        }

    fun kotlinDlsGradlePlugin(): String =
        "org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:$expectedKotlinDslPluginsVersion"

    @get:Inject
    @get:HiddenInDefinition
    val targetProject: Project
}
