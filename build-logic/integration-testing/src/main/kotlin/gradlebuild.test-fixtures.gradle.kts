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

import gradlebuild.basics.accessors.groovy

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
    id("gradlebuild.dependency-modules")
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

// do not attempt to find projects when the plugin is applied just to generate accessors
if (project.name != "gradle-kotlin-dsl-accessors" && project.name != "test" /* remove once wrapper is updated */) {
    dependencies {
        testFixturesApi(project(":internal-testing"))
        // platform
        testFixturesImplementation(platform(project(":distributions-dependencies")))

        // add a set of default dependencies for fixture implementation
        testFixturesImplementation(libs.junit)
        testFixturesImplementation(libs.groovy)
        testFixturesImplementation(libs.spock)
        testFixturesRuntimeOnly(libs.bytebuddy)
        testFixturesRuntimeOnly(libs.cglib)
    }
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
            val testFixtures = sourceSets.testFixtures.get()
            testSources.from(testFixtures.java.srcDirs)
            testSources.from(testFixtures.groovy.srcDirs)
            testResources.from(testFixtures.resources.srcDirs)
        }
    }
}
