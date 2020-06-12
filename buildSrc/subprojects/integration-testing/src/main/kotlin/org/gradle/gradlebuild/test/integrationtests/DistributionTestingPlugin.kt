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

package org.gradle.gradlebuild.test.integrationtests

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanupExtension
import org.gradle.kotlin.dsl.*
import kotlin.collections.set


class DistributionTestingPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.withType<DistributionTest>().configureEach {
            dependsOn(":cleanUpCaches")
            finalizedBy(":cleanUpDaemons")
            shouldRunAfter("test")

            setJvmArgsOfTestJvm()
            setSystemPropertiesOfTestJVM(project)
            configureGradleTestEnvironment(
                rootProject.layout,
                configurations
            )
            addSetUpAndTearDownActions(project)
        }
    }

    private
    fun executerRequiresDistribution(taskName: String) = !taskName.startsWith("embedded") || taskName.contains("CrossVersion") // <- Tooling API [other-version]->[current]

    private
    fun executerRequiresFullDistribution(taskName: String) = taskName.startsWith("noDaemon")

    private
    fun DistributionTest.addSetUpAndTearDownActions(project: Project) {
        val cleanupExtension = project.rootProject.extensions.getByType(CleanupExtension::class.java)
        this.tracker.set(cleanupExtension.tracker)
    }

    private
    fun DistributionTest.configureGradleTestEnvironment(rootLayout: ProjectLayout, configurations: ConfigurationContainer) {
        val taskName = name
        val rootProjectDirectory = rootLayout.projectDirectory

        gradleInstallationForTest.apply {
            if (executerRequiresDistribution(taskName)) {
                gradleHomeDir.setFrom(if (executerRequiresFullDistribution(taskName)) {
                    configurations["${prefix}TestFullDistributionRuntimeClasspath"]
                } else {
                    configurations["${prefix}TestDistributionRuntimeClasspath"]
                })
            }
            // Set the base user home dir to be share by integration tests.
            // The actual user home dir will be a subfolder using the name of the distribution.
            gradleUserHomeDir.set(rootProjectDirectory.dir("intTestHomeDir"))
            // The user home dir is not wiped out by clean. Move the daemon working space underneath the build dir so they don't pile up on CI.
            // The actual daemon registry dir will be a subfolder using the name of the distribution.
            daemonRegistry.set(rootLayout.buildDirectory.dir("daemon"))
            gradleSnippetsDir.set(rootLayout.projectDirectory.dir("subprojects/docs/src/snippets"))
        }

        // Wire the different inputs for local distributions and repos that are declared by dependencies in the build scripts
        normalizedDistributionZip.distributionZip.setFrom(configurations["${prefix}TestNormalizedDistributionPath"])
        binDistributionZip.distributionZip.setFrom(configurations["${prefix}TestBinDistributionPath"])
        allDistributionZip.distributionZip.setFrom(configurations["${prefix}TestAllDistributionPath"])
        docsDistributionZip.distributionZip.setFrom(configurations["${prefix}TestDocsDistributionPath"])
        srcDistributionZip.distributionZip.setFrom(configurations["${prefix}TestSrcDistributionPath"])
        localRepository.localRepo.setFrom(configurations["${prefix}TestLocalRepositoryPath"])
    }

    private
    fun DistributionTest.setJvmArgsOfTestJvm() {
        jvmArgs("-Xmx512m", "-XX:+HeapDumpOnOutOfMemoryError")
        if (!javaVersion.isJava8Compatible) {
            jvmArgs("-XX:MaxPermSize=768m")
        }
    }

    private
    fun DistributionTest.setSystemPropertiesOfTestJVM(project: Project) {
        // use -PtestVersions=all or -PtestVersions=1.2,1.3…
        val integTestVersionsSysProp = "org.gradle.integtest.versions"
        if (project.hasProperty("testVersions")) {
            systemProperties[integTestVersionsSysProp] = project.property("testVersions")
        } else {
            systemProperties[integTestVersionsSysProp] = "default"
        }
    }
}
