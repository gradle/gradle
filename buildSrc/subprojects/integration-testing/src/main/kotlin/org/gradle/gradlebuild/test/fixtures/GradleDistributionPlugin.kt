/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.gradlebuild.test.fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.gradlebuild.test.integrationtests.DistributionTest
import org.gradle.kotlin.dsl.*
import useAllDistribution
import java.util.concurrent.Callable


/**
 * This plugin adds the ability to build and use a Gradle distribution that may
 * be reduced to contain only the parts of Gradle needed for integration testing
 * the project that applies this plugin (partial distribution aka Integration Test Image).
 */
@Suppress("unused")
open class GradleDistributionPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        // core modules that actually end up in 'plugins/'
        val coreRuntimeExtensionProjects = listOf(
            "workers",
            "dependencyManagement",
            "testKit"
        )

        val intTestImage = tasks.register<Sync>("intTestImage") {
            group = "Verification"
            into(file("$buildDir/integ test"))
        }
        tasks.withType<DistributionTest>().configureEach {
            dependsOn(intTestImage)
        }

        // Buckets
        val apiBucket by configurations.bucket()
        val coreRuntime by configurations.bucket()
        val bundledPluginsRuntime by configurations.bucket()
        val fullGradleRuntime by configurations.bucket(coreRuntime, bundledPluginsRuntime)
        val coreRuntimeExtensions by configurations.bucket()

        val gradleApi by resolver(apiBucket)
        val coreRuntimeClasspath by resolver(coreRuntime)
        val bundledPluginsRuntimeClasspath by resolver(bundledPluginsRuntime)
        val fullGradleRuntimeClasspath by resolver(fullGradleRuntime)

        val gradleScripts by resolver()
        gradleScripts.attributes {
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("start-scripts"))
        }

        dependencies {
            rootProject.subprojects {
                val subproject = this
                subproject.plugins.withId("gradlebuild.distribution.core") {
                    if (subproject.name !in coreRuntimeExtensionProjects) {
                        coreRuntime(subproject)
                    } else {
                        bundledPluginsRuntime(subproject)
                        coreRuntimeExtensions(subproject)
                    }
                }
                subproject.plugins.withId("gradlebuild.distribution.plugins") {
                    bundledPluginsRuntime(subproject)
                    if (subproject.name in coreRuntimeExtensionProjects) {
                        coreRuntimeExtensions(subproject)
                    }
                }
                subproject.plugins.withId("gradlebuild.distribution.api") {
                    apiBucket(subproject)
                }
            }
            gradleScripts.withDependencies {
                gradleScripts(project(":launcher"))
            }
        }

        if (useAllDistribution) {
            val unpackedPath = layout.buildDirectory.dir("tmp/unpacked-all-distribution")

            val unpackAllDistribution = tasks.register<Sync>("unpackAllDistribution") {
                dependsOn(":distributions:allZip")
                // TODO: This should be modelled as a publication
                from(Callable {
                    val distributionsProject = rootProject.project("distributions")
                    val allZip = distributionsProject.tasks.getByName<Zip>("allZip")
                    zipTree(allZip.archiveFile)
                })
                into(unpackedPath)
            }

            intTestImage.configure {
                dependsOn(unpackAllDistribution)
                from(unpackedPath.get().dir("gradle-$version"))
            }
        } else {
            val partialDistributionPluginsClasspath = partialDistributionPluginsClasspath(bundledPluginsRuntimeClasspath)
            intTestImage.configure {
                into("bin") {
                    from(gradleScripts)
                    fileMode = Integer.parseInt("0755", 8)
                }

                into("lib") {
                    from(coreRuntimeClasspath)
                    into("plugins") {
                        from(partialDistributionPluginsClasspath - coreRuntimeClasspath)
                    }
                }

                preserve {
                    include("samples/**")
                }

                doLast {
                    ant.withGroovyBuilder {
                        "chmod"("dir" to "$destinationDir/bin", "perm" to "ugo+rx", "includes" to "**/*")
                    }
                }
            }
        }
    }

    private
    fun Project.partialDistributionPluginsClasspath(bundledPluginsRuntimeClasspath: Configuration): FileCollection {
        // Essentials that are always needed to run proper Gradle build (Related: DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES / GRADLE_OPTIONAL_EXTENSION_MODULES)
        val essentialRuntimeExtensions = listOf(
            ":instantExecution",
            ":pluginUse",
            ":kotlinDslProviderPlugins",
            ":kotlinDslToolingBuilders")

        val partialDistributionPluginsClasspath by resolver()

        configurations.all {
            // this is an approximation as we build only one distribution per project: this collects all test runtime classpathes
            if (isTestRuntimeClasspath()) {
                partialDistributionPluginsClasspath.extendsFrom(this)
            }
        }
        dependencies {
            partialDistributionPluginsClasspath.withDependencies {
                // Runtime essentials that are not part of the core
                essentialRuntimeExtensions.forEach { partialDistributionPluginsClasspath(project(it)) }
                // My own dependencies (includes core dependencies, which we will substract)
                partialDistributionPluginsClasspath(this@partialDistributionPluginsClasspath)
            }
        }

        val dependenciesNotBelongingToGradle = partialDistributionPluginsClasspath - bundledPluginsRuntimeClasspath
        return partialDistributionPluginsClasspath - dependenciesNotBelongingToGradle
    }

    private
    fun ConfigurationContainer.bucket(vararg extends: Configuration): NamedDomainObjectContainerCreatingDelegateProvider<Configuration> =
        creating {
            isCanBeResolved = false
            isCanBeConsumed = false
            isVisible = false
            extends.forEach { extendsFrom(it) }
        }

    private
    fun Project.resolver(vararg extends: Configuration): NamedDomainObjectContainerCreatingDelegateProvider<Configuration> =
        configurations.creating {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            }
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
            extends.forEach { extendsFrom(it) }
        }

    private
    fun Configuration.isTestRuntimeClasspath() = isCanBeResolved && name.endsWith(
        JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME.substring(1))
}
