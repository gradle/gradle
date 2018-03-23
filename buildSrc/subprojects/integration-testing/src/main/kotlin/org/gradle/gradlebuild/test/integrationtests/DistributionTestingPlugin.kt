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

import accessors.java
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanUpDaemons
import org.gradle.kotlin.dsl.*
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


class DistributionTestingPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.withType<DistributionTest> {
            dependsOn(":toolingApi:toolingApiShadedJar")
            dependsOn(":cleanUpCaches")
            finalizedBy(":cleanUpDaemons")
            shouldRunAfter("test")

            setJvmArgsOfTestJvm()
            setSystemPropertiesOfTestJVM(project)
            configureGradleTestEnvironment(this)
            setDedicatedTestOutputDirectoryPerTask(this)
            addSetUpAndTearDownActions(this)
        }
    }

    private
    fun Project.addSetUpAndTearDownActions(distributionTest: DistributionTest) {
        lateinit var daemonListener: Any

        // TODO Why don't we register with the test listener of the test task
        // We would not need to do late configuration and need to add a global listener
        // We now add multiple global listeners stepping on each other
        distributionTest.doFirst {
            // TODO Refactor to not reach into tasks of another project
            val cleanUpDaemons: CleanUpDaemons by rootProject.tasks
            daemonListener = cleanUpDaemons.newDaemonListener()
            gradle.addListener(daemonListener)
        }

        // TODO Remove once we go to task specific listeners.
        distributionTest.doLast {
            gradle.removeListener(daemonListener)
        }
    }

    private
    fun Project.setDedicatedTestOutputDirectoryPerTask(distributionTest: DistributionTest) {
        distributionTest.reports.junitXml.destination = File(java.testResultsDir, distributionTest.name)
        // TODO Confirm that this is not needed
        afterEvaluate {
            distributionTest.reports.html.destination = file("${the<ReportingExtension>().baseDir}/$name")
        }
    }

    private
    fun Project.configureGradleTestEnvironment(distributionTest: DistributionTest): Unit = distributionTest.run {
        gradleInstallationForTest.run {
            val intTestImage: Sync by tasks
            val toolingApiShadedJar: Zip by rootProject.project(":toolingApi").tasks
            gradleHomeDir.set(dir { intTestImage.destinationDir })
            gradleUserHomeDir.set(rootProject.layout.projectDirectory.dir("intTestHomeDir"))
            daemonRegistry.set(rootProject.layout.buildDirectory.dir("daemon"))
            toolingApiShadedJarDir.set(dir { toolingApiShadedJar.destinationDir })
        }

        libsRepository.dir.set(rootProject.layout.projectDirectory.dir("build/repo"))

        binaryDistributions.run {
            distsDir.set(dir { rootProject.the<BasePluginConvention>().distsDir })
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

        val mirrorUrls = collectMirrorUrls()
        val mirrors = listOf("mavencentral", "jcenter", "lightbendmaven", "ligthbendivy", "google")
        mirrors.forEach { mirror ->
            systemProperties["org.gradle.integtest.mirrors.$mirror"] = mirrorUrls[mirror] ?: ""
        }
    }

    fun Project.dir(directory: () -> File): Provider<Directory> {
        return layout.buildDirectory.dir(provider { directory().absolutePath })
    }

    fun collectMirrorUrls(): Map<String, String> =
    // expected env var format: repo1_id:repo1_url,repo2_id:repo2_url,...
        System.getenv("REPO_MIRROR_URLS")?.split(',')?.associate { nameToUrl ->
            val (name, url) = nameToUrl.split(':', limit = 2)
            name to url
        } ?: emptyMap()
}
