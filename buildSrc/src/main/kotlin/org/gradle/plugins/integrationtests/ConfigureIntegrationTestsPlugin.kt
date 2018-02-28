/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.plugins.integrationtests

import accessors.idea
import accessors.eclipse
import accessors.java
import accessors.groovy
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.build.ReleasedVersionsFromVersionControl
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.compile.AvailableJavaInstallations
import org.gradle.plugins.testfixtures.TestFixturesExtension
import org.gradle.testing.CrossVersionTest
import org.gradle.testing.IntegrationTest

class ConfigureIntegrationTestsPlugin : Plugin<Project> {
    private
    val Project.hasIntegrationTests
        get() = project.file("src/integTest").isDirectory

    private
    val Project.hasCrossVersionTests
        get() = project.file("src/crossVersionTest").isDirectory

    override fun apply(project: Project): Unit = project.run {
        val integTestSourceSet = addIntegTestSourceSet()
        val crossVersionTestSourceSet = addCrossVersionTestSourceSet()
        // TODO Model as an extension object. The name is also misleading, as this applies to integration tests as well as cross version tests.
        val integTestTasks by extra { tasks.withType<IntegrationTest>() }
        baseConfigurationForIntegrationAndCrossVersionTestTasks(integTestTasks)

        if (hasIntegrationTests) {
            createIntegrationTestConfigurations()
            val integTestCompile by configurations
            val integTestRuntime by configurations
            addIntegrationTestDependencies(integTestCompile, integTestRuntime)
            baseConfigurationForIntegrationTestTasks(integTestSourceSet, integTestTasks)
            createIntegrationTestTasks()
            configureIdeForIntegrationTests(integTestSourceSet, integTestCompile, integTestRuntime)
        }

        if (hasCrossVersionTests) {
            createCrossVersionTestConfigurations()
            val crossVersionTestCompile by configurations
            val crossVersionTestRuntime by configurations
            addCrossVersionTestDependencies(crossVersionTestCompile, crossVersionTestRuntime)
            baseConfigurationForCrossVersionTestTasks(crossVersionTestSourceSet)
            createCrossVersionTestTasks()
            configureTestFixturesForCrossVersionTests()
            configureIdeForCrossVersionTests(crossVersionTestSourceSet)
        }
    }



    private fun Project.configureTestFixturesForCrossVersionTests() {
        configure<TestFixturesExtension> {
            from(":toolingApi")
        }
    }

    private
    fun Project.baseConfigurationForIntegrationAndCrossVersionTestTasks(integTestTasks: DomainObjectCollection<IntegrationTest>) {
        // TODO Check whether we really need to specify a global rule or can just configure all tasks created by this plugin
        integTestTasks.all {
            group = "verification"
            exclude(ExcludedTests.excludesForJavaVersion(currentTestJavaVersion))
        }
    }

    private
    fun baseConfigurationForIntegrationTestTasks(integTestSourceSet: SourceSet, integTestTasks: DomainObjectCollection<IntegrationTest>) {
        // TODO Check whether we really need to specify a global rule or can just configure all tasks created by this plugin
        integTestTasks.all {
            testClassesDirs = integTestSourceSet.output.classesDirs
            classpath = integTestSourceSet.runtimeClasspath
        }
    }

    private
    fun Project.baseConfigurationForCrossVersionTestTasks(crossVersionTestSourceSet: SourceSet) {
        // TODO Model as an extension object
        val crossVersionTestTasks by extra { tasks.withType<CrossVersionTest>() }

        // TODO Check whether we really need to specify a global rule or can just configure all tasks created by this plugin
        crossVersionTestTasks.all {
            testClassesDirs = crossVersionTestSourceSet.output.classesDirs
            classpath = crossVersionTestSourceSet.runtimeClasspath
            libsRepository.required = true
        }
    }

    private fun Project.createIntegrationTestTasks() {
        tasks {
            "integTest"(IntegrationTest::class) {
                val defaultExecuter = project.findProperty("defaultIntegTestExecuter") as? String ?: "embedded"
                description = "Runs integTests with '$defaultExecuter' executer"
                systemProperties["org.gradle.integtest.executer"] = defaultExecuter
                // TODO Move magic property out
                if (project.hasProperty("org.gradle.integtest.debug")) {
                    systemProperties["org.gradle.integtest.debug"] = "true"
                    testLogging.showStandardStreams = true
                }
                // TODO Move magic property out
                if (project.hasProperty("org.gradle.integtest.verbose")) {
                    testLogging.showStandardStreams = true
                }
                // TODO Move magic property out
                if (project.hasProperty("org.gradle.integtest.launcher.debug")) {
                    systemProperties["org.gradle.integtest.launcher.debug"] = "true"
                }
            }

            "check" { dependsOn("integTest") }
            listOf("embedded", "forking", "noDaemon", "parallel").forEach { mode ->
                "${mode}IntegTest"(IntegrationTest::class) {
                    description = "Runs integTests with '$mode' executer"
                    systemProperties["org.gradle.integtest.executer"] = mode
                }
            }
        }
    }

    private
    fun Project.createCrossVersionTestTasks() {
        // Calculate the set of released versions - do this at configuration time because we need this to create various tasks
        tasks {
            "crossVersionTest"(CrossVersionTest::class) {
                val defaultExecuter = project.findProperty("defaultIntegTestExecuter") as? String ?: "embedded"
                description = "Runs crossVersionTest with '$defaultExecuter' executer"
                systemProperties["org.gradle.integtest.executer"] = defaultExecuter

                if (project.hasProperty("org.gradle.integtest.debug")) {
                    systemProperties["org.gradle.integtest.debug"] = "true"
                    testLogging.showStandardStreams = true
                }
                if (project.hasProperty("org.gradle.integtest.verbose")) {
                    testLogging.showStandardStreams = true
                }
                if (project.hasProperty("org.gradle.integtest.launcher.debug")) {
                    systemProperties["org.gradle.integtest.launcher.debug"] = "true"
                }

                "check" { dependsOn("crossVersionTest") }

                listOf("embedded", "forking").forEach { mode ->
                    "${mode}CrossVersionTest"(CrossVersionTest::class) {
                        description = "Runs crossVersionTests with '$mode' executer"
                        systemProperties["org.gradle.integtest.executer"] = mode
                    }
                }

                val allVersionsCrossVersionTests by creating {
                    group = "verification"
                    description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
                }

                val quickFeedbackCrossVersionTests by creating {
                    group = "verification"
                    description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
                }

                val releasedVersions = this@createCrossVersionTestTasks.property("releasedVersions") as ReleasedVersionsFromVersionControl
                val quickTestVersions = releasedVersions.getTestedVersions(true)
                releasedVersions.getTestedVersions(false).forEach { targetVersion ->
                    "gradle${targetVersion}CrossVersionTest"(CrossVersionTest::class) {
                        allVersionsCrossVersionTests.dependsOn(this)
                        description = "Runs the cross-version tests against Gradle $targetVersion"
                        systemProperties["org.gradle.integtest.versions"] = targetVersion
                        systemProperties["org.gradle.integtest.executer"] = "forking"
                        if (targetVersion in quickTestVersions) {
                            quickFeedbackCrossVersionTests.dependsOn(this)
                        }
                    }
                }
            }
        }
    }

    private
    fun Project.createIntegrationTestConfigurations() {
        configurations {
            // TODO Distinguish between getting and creating
            "integTestCompile" { extendsFrom(configurations["testCompile"]) }
            "integTestRuntime" { extendsFrom(configurations["testRuntime"]) }
            "integTestImplementation" { extendsFrom(configurations["testImplementation"]) }
            "partialDistribution" { extendsFrom(configurations["integTestRuntimeClasspath"]) }
        }
    }

    private
    fun Project.createCrossVersionTestConfigurations() {
        configurations {
            // TODO Distinguish between getting and creating
            "crossVersionTestCompile" { extendsFrom(configurations["testCompile"]) }
            "crossVersionTestImplementation" { extendsFrom(configurations["testImplementation"]) }
            "crossVersionTestRuntime" { extendsFrom(configurations["testRuntime"]) }
            "crossVersionTestRuntimeOnly" { extendsFrom(configurations["testRuntimeOnly"]) }
            "partialDistribution" { extendsFrom(configurations["crossVersionTestRuntimeClasspath"]) }
        }
    }

    private
    fun Project.addIntegrationTestDependencies(integTestCompile: Configuration,
                                integTestRuntime: Configuration) {
        dependencies {
            integTestCompile(project(":internalIntegTesting"))

            //so that implicit help tasks are available:
            integTestRuntime(project(":diagnostics"))

            //So that the wrapper and init task are added when integTests are run via commandline
            integTestRuntime(project(":buildInit"))
            //TODO above can be removed when we implement the auto-apply plugins
        }
    }

    private
    fun Project.addCrossVersionTestDependencies(crossVersionTestCompile: Configuration,
                                crossVersionTestRuntime: Configuration) {
        dependencies {
            crossVersionTestCompile(project(":internalIntegTesting"))

            //so that implicit help tasks are available:
            crossVersionTestRuntime(project(":diagnostics"))

            //So that the wrapper and init task are added when crossVersionTests are run via commandline
            crossVersionTestRuntime(project(":buildInit"))
            //TODO above can be removed when we implement the auto-apply plugins

            crossVersionTestRuntime(project(":toolingApiBuilders"))
        }
    }

    private
    fun Project.configureIdeForIntegrationTests(integTestSourceSet: SourceSet,
                             integTestCompile: Configuration,
                             integTestRuntime: Configuration) {
        // lazy as plugin not applied yet
        idea {
            module {
                testSourceDirs = testSourceDirs + integTestSourceSet.groovy.srcDirs + integTestSourceSet.resources.srcDirs
                scopes["TEST"]!!["plus"]!!.apply {
                    add(integTestCompile)
                    add(integTestRuntime)
                }
            }
        }

        // lazy as plugin not applied yet
        eclipse {
            classpath.plusConfigurations.apply {
                add(integTestCompile)
                add(integTestRuntime)
            }
        }
    }

    private
    fun Project.configureIdeForCrossVersionTests(crossVersionTestSourceSet: SourceSet) {
        // lazy as plugin not applied yet
        idea {
            module {
                testSourceDirs = testSourceDirs + crossVersionTestSourceSet.groovy.srcDirs
                testSourceDirs = testSourceDirs + crossVersionTestSourceSet.resources.srcDirs
                scopes["TEST"]!!["plus"]!!.apply {
                    // TODO Check whether we can not use the parameter configurations
                    add(configurations["crossVersionTestCompileClasspath"])
                    add(configurations["crossVersionTestRuntimeClasspath"])
                }
            }
        }

        // lazy as plugin not applied yet
        eclipse {
            classpath.plusConfigurations.apply {
                // TODO Check whether we can not use the parameter configurations
                add(configurations["crossVersionTestCompileClasspath"])
                add(configurations["crossVersionTestRuntimeClasspath"])
            }
        }
    }

    private
    fun Project.addIntegTestSourceSet(): SourceSet {
        val integTest by java.sourceSets.creating {
            val main by this@addIntegTestSourceSet.java.sourceSets
            compileClasspath += main.output
            runtimeClasspath += main.output
        }
        return integTest
    }

    private
    fun Project.addCrossVersionTestSourceSet(): SourceSet {
        val crossVersionTest by java.sourceSets.creating {
            val main by this@addCrossVersionTestSourceSet.java.sourceSets
            compileClasspath += main.output
            runtimeClasspath += main.output
        }
        return crossVersionTest
    }

    private
    val Project.currentTestJavaVersion
        get() = rootProject.the<AvailableJavaInstallations>().javaInstallationForTest.javaVersion
}
