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

import accessors.base
import accessors.java
import accessors.reporting
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Sync
import org.gradle.gradlebuild.packaging.ShadedJar
import org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanUpDaemons
import org.gradle.kotlin.dsl.*
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


class DistributionTestingPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.withType<DistributionTest>().configureEach {
            dependsOn(":toolingApi:toolingApiShadedJar")
            dependsOn(":cleanUpCaches")
            finalizedBy(":cleanUpDaemons")
            shouldRunAfter("test")

            setJvmArgsOfTestJvm()
            setSystemPropertiesOfTestJVM(project)
            configureGradleTestEnvironment(rootProject.providers, rootProject.layout, rootProject.base)
            setDedicatedTestOutputDirectoryPerTask(java, reporting)
            addSetUpAndTearDownActions(gradle)
        }
    }

    private
    fun DistributionTest.addSetUpAndTearDownActions(gradle: Gradle) {
        lateinit var daemonListener: Any

        // TODO Why don't we register with the test listener of the test task
        // We would not need to do late configuration and need to add a global listener
        // We now add multiple global listeners stepping on each other
        doFirst {
            // TODO Refactor to not reach into tasks of another project
            val cleanUpDaemons: CleanUpDaemons by gradle.rootProject.tasks
            daemonListener = cleanUpDaemons.newDaemonListener()
            gradle.addListener(daemonListener)
        }

        // TODO Remove once we go to task specific listeners.
        doLast {
            gradle.removeListener(daemonListener)
        }
    }

    private
    fun DistributionTest.setDedicatedTestOutputDirectoryPerTask(java: JavaPluginConvention, reporting: ReportingExtension) {
        reports.junitXml.destination = File(java.testResultsDir, name)
        val htmlDirectory = reporting.baseDirectory.dir(this.name)
        project.afterEvaluate {
            // TODO: Replace this with a Provider
            reports.html.destination = htmlDirectory.get().asFile
        }
    }

    private
    fun DistributionTest.configureGradleTestEnvironment(providers: ProviderFactory, layout: ProjectLayout, basePluginConvention: BasePluginConvention) {
        // TODO: Replace this with something in the Gradle API to make this transition easier
        fun dirWorkaround(directory: () -> File): Provider<Directory> =
            layout.directoryProperty(layout.projectDirectory.dir(providers.provider { directory().absolutePath }))

        gradleInstallationForTest.apply {
            // TODO Refactor to not reach into tasks of another project
            val intTestImage: Sync by project.tasks
            val toolingApiShadedJar: ShadedJar by project.rootProject.project(":toolingApi").tasks
            gradleUserHomeDir.set(layout.projectDirectory.dir("intTestHomeDir"))
            daemonRegistry.set(layout.buildDirectory.dir("daemon"))
            gradleHomeDir.set(dirWorkaround { intTestImage.destinationDir })
            toolingApiShadedJarDir.set(dirWorkaround { toolingApiShadedJar.jarFile.get().asFile.parentFile })
        }

        libsRepository.dir.set(layout.projectDirectory.dir("build/repo"))

        binaryDistributions.apply {
            distsDir.set(dirWorkaround({ basePluginConvention.distsDir }))
            distZipVersion = project.version.toString()
        }
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
        }
        if (integTestVersionsSysProp !in systemProperties) {
            systemProperties[integTestVersionsSysProp] = "latest"
        }

        fun ifProperty(name: String, then: String): String? =
            then.takeIf { project.findProperty(name) == true }

        systemProperties["org.gradle.integtest.native.toolChains"] =
            ifProperty("testAllPlatforms", "all") ?: "default"

        systemProperties["org.gradle.integtest.multiversion"] =
            ifProperty("testAllVersions", "all") ?: "default"
    }
}
