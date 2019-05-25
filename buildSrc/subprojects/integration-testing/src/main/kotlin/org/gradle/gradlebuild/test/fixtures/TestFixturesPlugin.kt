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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import testLibrary
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
        extensions.create<TestFixturesExtension>("testFixtures")

        if (file("src/testFixtures").isDirectory) {
            configureAsProducer()
        }

        configureAsConsumer()
    }

    /**
     * This mimics what the java-library plugin does, but creating a library of test fixtures instead.
     */
    private
    fun Project.configureAsProducer() {
        val main by java.sourceSets
        val testFixtures by java.sourceSets.creating {
            extendsFrom(main, configurations)
        }
        java.sourceSets.named("test", SourceSet::class) {
            extendsFrom(testFixtures, configurations)
        }

        java.sourceSets.matching { it.name.toLowerCase(Locale.ROOT).endsWith("test") }.all {
            compileClasspath += testFixtures.output
            runtimeClasspath += testFixtures.output
        }

        configurations {
            val testFixturesApi by creating
            val testFixturesImplementation by getting {
                extendsFrom(testFixturesApi)
            }
            val testFixturesRuntimeOnly by getting

            create(testFixtures.apiElementsConfigurationName) {
                extendsFrom(testFixturesApi)
                /*
                 * FIXME only the classes would be more appropriate here, but the Groovy compiler
                 * needs resources too because we make use of extension methods registered through
                 * a META-INF file.
                 */
                afterEvaluate {
                    testFixtures.output.forEach {
                        outgoing.artifact(it) {
                            builtBy(testFixtures.output)
                        }
                    }
                }
            }

            create(testFixtures.runtimeElementsConfigurationName) {
                extendsFrom(testFixturesImplementation, testFixturesRuntimeOnly)
                /*
                 * FIXME a JAR would be more appropriate here, but the PlayApp fixture assumes that
                 * its class is loaded from a directory.
                 */
                afterEvaluate {
                    testFixtures.output.forEach {
                        outgoing.artifact(it) {
                            builtBy(testFixtures.output)
                        }
                    }
                }
            }
        }

        removeTestFixturesFromArchivesConfiguration()

        dependencies {
            val testFixturesApi by configurations
            val testFixturesImplementation by configurations
            val testFixturesRuntimeOnly by configurations

            // add the implementation code as 'test api' of the project providing the fixtues
            testFixturesApi(project(path))
            // add a set of default dependencies for fixture implementation
            testFixturesImplementation(library("junit"))
            testFixturesImplementation(library("groovy"))
            testFixturesImplementation(testLibrary("spock"))
            testFixturesRuntimeOnly(testLibrary("bytebuddy"))
            testFixturesRuntimeOnly(testLibrary("cglib"))
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

    private
    fun SourceSet.extendsFrom(other: SourceSet, configurations: ConfigurationContainer) {
        configurations {
            implementationConfigurationName {
                extendsFrom(configurations[other.implementationConfigurationName])
            }
            runtimeOnlyConfigurationName {
                extendsFrom(configurations[other.runtimeOnlyConfigurationName])
            }
        }
    }

    private
    fun Project.configureAsConsumer() = afterEvaluate {
        the<TestFixturesExtension>().origins.forEach { (projectPath, sourceSetName) ->
            val sourceSet = java.sourceSets[sourceSetName]
            val implementationConfig = sourceSet.implementationConfigurationName
            val runtimeOnlyConfig = sourceSet.runtimeOnlyConfigurationName

            dependencies {
                implementationConfig(project(path = projectPath, configuration = "testFixturesApiElements"))
                implementationConfig(project(":internalTesting"))
                runtimeOnlyConfig(project(path = projectPath, configuration = "testFixturesRuntimeElements"))
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
