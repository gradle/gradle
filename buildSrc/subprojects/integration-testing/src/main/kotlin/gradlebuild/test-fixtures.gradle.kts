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
package gradlebuild

import library
import libraries
import testLibrary

import gradlebuild.basics.accessors.groovy

import org.gradle.api.plugins.internal.JvmPluginsHelper
import org.gradle.plugins.ide.idea.model.IdeaModel

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
plugins {
    `java-library`
    `java-test-fixtures`
}

// The below mimics what the java-library plugin does, but creating a library of test fixtures instead.

sourceSets.matching { it.name.endsWith("Test") }.all {
    // the 'test' (with lower case 't') source set is already configured to use test fixtures by the JavaTestFixturesPlugin
    configurations[implementationConfigurationName].dependencies.add(
        dependencies.testFixtures(project)
    )
}

val testFixturesApi by configurations
val testFixturesImplementation by configurations
val testFixturesRuntimeOnly by configurations
val testFixturesRuntimeElements by configurations
val testFixturesApiElements by configurations

// Required due to: https://github.com/gradle/gradle/issues/13278
testFixturesRuntimeElements.extendsFrom(testFixturesRuntimeOnly)

if (!libraries.isEmpty()) { // we cannot access library() when compiling this script - https://github.com/gradle/gradle-private/issues/3098
    dependencies {
        testFixturesApi(project(":internalTesting"))
        // platform
        testFixturesImplementation(platform(project(":distributionsDependencies")))
        // add a set of default dependencies for fixture implementation
        testFixturesImplementation(library("junit"))
        testFixturesImplementation(library("groovy"))
        testFixturesImplementation(testLibrary("spock"))
        testFixturesRuntimeOnly(testLibrary("bytebuddy"))
        testFixturesRuntimeOnly(testLibrary("cglib"))
    }
}


// Add an outgoing variant allowing to select the exploded resources directory
// as this is required at least by one project (idePlay)
val processResources = tasks.named<ProcessResources>("processTestFixturesResources")
testFixturesRuntimeElements.outgoing.variants.maybeCreate("resources").run {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.RESOURCES))

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
            testSourceDirs = testSourceDirs + sourceSets.testFixtures.get().groovy.srcDirs
            testResourceDirs = testResourceDirs + sourceSets.testFixtures.get().resources.srcDirs
        }
    }
}
