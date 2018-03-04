package org.gradle.testing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Zip
import org.gradle.cleanup.CleanUpCaches
import org.gradle.cleanup.CleanUpDaemons
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.kotlin.dsl.*
import org.gradle.process.KillLeakingJavaProcesses
import java.io.File

open class DistributionTestingExtension(objects: ObjectFactory) {
    val toolingApiShadedJarTask: Property<Zip> = objects.property()
    val intTestImageTask: Property<Sync> = objects.property()
    val distributionZipTasks: Property<Map<String, Zip>> = objects.property()
    val publishLocalArchivesTask: Property<Upload> = objects.property()
}

open class DistributionTestingPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        fun dir(directory: () -> File): Provider<Directory> {
            return layout.buildDirectory.dir(provider { directory().absolutePath })
        }

        fun collectMirrorUrls(): Map<String, String> =
        // expected env var format: repo1_id:repo1_url,repo2_id:repo2_url,...
            System.getenv("REPO_MIRROR_URLS")?.split(',')?.associate { nameToUrl ->
                val (name, url) = nameToUrl.split(':', limit = 2)
                name to url
            } ?: emptyMap()

        tasks.withType<DistributionTest> {
            dependsOn(":toolingApi:toolingApiShadedJar")
            dependsOn(":cleanUpCaches")
            finalizedBy(":cleanUpDaemons")
            shouldRunAfter("test")

            jvmArgs("-Xmx512m", "-XX:+HeapDumpOnOutOfMemoryError")
            if (!javaVersion.isJava8Compatible) {
                jvmArgs("-XX:MaxPermSize=768m")
            }

            reports.junitXml.destination = the<JavaPluginConvention>().testResultsDir.resolve(name)

            // use -PtestVersions=all or -PtestVersions=1.2,1.3…
            val integTestVersionsSysProp = "org.gradle.integtest.versions"
            if (project.hasProperty("testVersions")) {
                systemProperties[integTestVersionsSysProp] = project.property("testVersions")
            }
            if (integTestVersionsSysProp !in systemProperties) {
                systemProperties[integTestVersionsSysProp] = "latest"
            }

            fun Project.ifProperty(name: String, then: String): String? =
                then.takeIf { findProperty(name) == true }

            systemProperties["org.gradle.integtest.native.toolChains"] =
                ifProperty("testAllPlatforms", "all") ?: "default"

            systemProperties["org.gradle.integtest.multiversion"] =
                ifProperty("testAllVersions", "all") ?: "default"

            val mirrorUrls = collectMirrorUrls()
            val mirrors = listOf("mavencentral", "jcenter", "lightbendmaven", "ligthbendivy", "google")
            mirrors.forEach { mirror ->
                systemProperties["org.gradle.integtest.mirrors.$mirror"] = mirrorUrls[mirror] ?: ""
            }

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

            project.afterEvaluate {
                reports.html.destination = the<ReportingExtension>().baseDir.resolve(this@withType.name)
            }

            lateinit var daemonListener: Any

            doFirst {
                val cleanUpDaemons: CleanUpDaemons by rootProject.tasks
                daemonListener = cleanUpDaemons.newDaemonListener()
                gradle.addListener(daemonListener)
            }

            doLast {
                gradle.removeListener(daemonListener)
            }
        }

        project(":") {

            if (tasks.findByName("cleanUpCaches") != null) {
                return@project
            }

            tasks {

                "cleanUpCaches"(CleanUpCaches::class) {
                    dependsOn(":createBuildReceipt")
                }

                "cleanUpDaemons"(CleanUpDaemons::class)

                val killExistingProcessesStartedByGradle by creating(KillLeakingJavaProcesses::class)

                if (BuildEnvironment.isCiServer) {
                    "clean" {
                        dependsOn(killExistingProcessesStartedByGradle)
                    }
                    subprojects {
                        tasks.all {
                            mustRunAfter(killExistingProcessesStartedByGradle)
                        }
                    }
                }
            }
        }
    }
}
