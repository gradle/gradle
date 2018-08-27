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

import org.gradle.kotlin.dsl.*

import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

import testLibraries
import testLibrary


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
@Suppress("unused")
open class TestFixturesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        apply(plugin = "java")

        extensions.create<TestFixturesExtension>("testFixtures")

        if (file("src/testFixtures").isDirectory) {
            configureAsProducer()
        }

        configureAsConsumer()
    }

    private
    fun Project.configureAsProducer() {

        configurations {
            create("outputDirs") {}

            create("testFixturesCompile") { extendsFrom(configurations["compile"]) }
            create("testFixturesImplementation") { extendsFrom(configurations["implementation"]) }
            create("testFixturesRuntime") { extendsFrom(configurations["runtime"]) }

            // Expose configurations that include the test fixture classes for clients to use
            create("testFixturesUsageCompile") {
                extendsFrom(configurations["testFixturesCompile"], configurations["outputDirs"])
            }
            create("testFixturesUsageRuntime") {
                extendsFrom(configurations["testFixturesRuntime"], configurations["testFixturesUsageCompile"])
            }

            // Assume that the project wants to use the fixtures for its tests
            "testCompile" { extendsFrom(configurations["testFixturesUsageCompile"]) }
            "testRuntime" { extendsFrom(configurations["testFixturesUsageRuntime"]) }
        }

        val outputDirs by configurations.getting
        val testFixturesCompile by configurations.getting
        val testFixturesRuntime by configurations.getting
        val testFixturesUsageCompile by configurations.getting

        val main by java.sourceSets
        val testFixtures by java.sourceSets.creating {
            compileClasspath = main.output + configurations["testFixturesCompileClasspath"]
            runtimeClasspath = output + compileClasspath + configurations["testFixturesRuntimeClasspath"]
        }

        dependencies {
            outputDirs(testFixtures.output)
            testFixturesUsageCompile(project(path))
            testFixturesCompile(library("junit"))
            testFixturesCompile(testLibrary("spock"))
            testLibraries("jmock").forEach { testFixturesCompile(it) }
        }

        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    testSourceDirs = testSourceDirs + testFixtures.groovy.srcDirs + testFixtures.resources.srcDirs
                }
            }
        }

        plugins.withType<EclipsePlugin> {
            configure<EclipseModel> {
                classpath {
                    plusConfigurations.add(testFixturesCompile)
                    plusConfigurations.add(testFixturesRuntime)

                    //avoiding the certain output directories from the classpath in Eclipse
                    minusConfigurations.add(outputDirs)
                }
            }
        }
    }

    private
    fun Project.configureAsConsumer() = afterEvaluate {

        the<TestFixturesExtension>().origins.forEach { (projectPath, sourceSetName) ->

            val compileConfig = if (sourceSetName == "main") "compile" else "${sourceSetName}Compile"
            val runtimeConfig = if (sourceSetName == "main") "runtime" else "${sourceSetName}Runtime"

            dependencies {
                compileConfig(project(path = projectPath, configuration = "testFixturesUsageCompile"))
                compileConfig(project(":internalTesting"))
                runtimeConfig(project(path = projectPath, configuration = "testFixturesUsageRuntime"))
            }
        }
    }
}
