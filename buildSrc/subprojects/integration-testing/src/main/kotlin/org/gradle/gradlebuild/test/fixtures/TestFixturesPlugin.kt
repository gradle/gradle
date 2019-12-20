/*
 * Copyright 2017 the original author or authors.
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

import accessors.groovy
import accessors.java
import library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.api.plugins.JavaTestFixturesPlugin
import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.language.jvm.tasks.ProcessResources
import testLibrary
import java.io.File
import java.util.Locale


/**
 * Test Fixtures Plugin.
 *
 * Configures the Project as a test fixtures producer if `src/testFixtures` is a directory:
 * - adds a new `testFixtures` source set which should contain utilities/fixtures to assist in unit testing
 *   classes from the main source set,
 * - the test fixtures are automatically made available to the test classpath.
 *
 * Configures the Project as a test fixtures consumer according to the `testFixtures` extension configuration.
 */
// TODO convert this to use variant aware dependency management
@Suppress("unused")
open class TestFixturesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        if (file("src/testFixtures").isDirectory) {
            configureAsProducer()
        }
    }

    /**
     * This mimics what the java-library plugin does, but creating a library of test fixtures instead.
     */
    private
    fun Project.configureAsProducer() {
        project.pluginManager.apply(JavaTestFixturesPlugin::class.java)

        java.sourceSets.matching { it.name.toLowerCase(Locale.ROOT).endsWith("test") }.all {
            if (name != "test") {
                // the main test source set is already configured to use test fixtures by the Java test fixtures plugin
                configurations.findByName(implementationConfigurationName)!!.dependencies.add(
                    dependencies.testFixtures(project)
                )
            }
        }

        val testFixtures by java.sourceSets.getting

        removeTestFixturesFromArchivesConfiguration()

        val testFixturesApi by configurations
        val testFixturesImplementation by configurations
        val testFixturesRuntimeOnly by configurations
        val testFixturesRuntimeElements by configurations
        val testFixturesApiElements by configurations

        dependencies {
            testFixturesApi(project(":internalTesting"))
            // add a set of default dependencies for fixture implementation
            testFixturesImplementation(library("junit"))
            testFixturesImplementation(library("groovy"))
            testFixturesImplementation(testLibrary("spock"))
            testFixturesRuntimeOnly(testLibrary("bytebuddy"))
            testFixturesRuntimeOnly(testLibrary("cglib"))
        }

        // Add an outgoing variant allowing to select the exploded resources directory
        // as this is required at least by one project (idePlay)
        val processResources = tasks.named<ProcessResources>("processTestFixturesResources")
        testFixturesRuntimeElements.outgoing.variants.maybeCreate("resources").run {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.RESOURCES))

            artifact(object : JvmPluginsHelper.IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY, processResources) {
                override fun getFile(): File {
                    return processResources.get().destinationDir
                }
            })
        }

        // Do not publish test fixture, we use them only internal for now
        val javaComponent = components["java"] as AdhocComponentWithVariants
        javaComponent.withVariantsFromConfiguration(testFixturesRuntimeElements) {
            skip()
        }
        javaComponent.withVariantsFromConfiguration(testFixturesApiElements) {
            skip()
        }

        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    testSourceDirs = testSourceDirs + testFixtures.groovy.srcDirs
                    testResourceDirs = testResourceDirs + testFixtures.resources.srcDirs
                }
            }
        }
    }

    // This is a hack to get rid of `Cannot publish artifact 'testFixtures' as it does not exist.`
    // https://builds.gradle.org/viewLog.html?buildId=15853642&buildTypeId=bt39
    private
    fun Project.removeTestFixturesFromArchivesConfiguration() = afterEvaluate {
        configurations["archives"]?.artifacts?.removeIf { it.name == "testFixtures" }
    }
}
