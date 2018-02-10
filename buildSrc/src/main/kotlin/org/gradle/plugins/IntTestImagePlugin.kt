package org.gradle.plugins

import config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask

import org.gradle.kotlin.dsl.*
import org.gradle.testing.DistributionTest
import java.util.concurrent.Callable

open class IntTestImagePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val intTestImage by tasks.creating(Sync::class) {
            group = "Verification"
            into(file("$buildDir/integ test"))
        }

        tasks.withType(DistributionTest::class.java) {
            dependsOn(intTestImage)
        }

        // This is terrible. We should have defined states.
        if (configurations.findByName("partialDistribution") == null) {
            configurations.create("partialDistribution")
        }
        val partialDistribution by configurations.getting

        if (config.useAllDistribution) {
            val unpackAllDistribution by tasks.creating(Sync::class) {
                val allZip by project.project("distributions").tasks.getting(AbstractArchiveTask::class)
                dependsOn(allZip)
                from(zipTree(allZip.archivePath))
                into("$buildDir/tmp/unpacked-all-distribution")
            }

            // Compensate for the top level dir in the zip
            val unpackedPath = "${unpackAllDistribution.destinationDir}/gradle-${version}}"

            intTestImage.dependsOn(unpackAllDistribution)
            intTestImage.from(unpackedPath)
        } else {
            val selfRunTime by configurations.creating

            afterEvaluate {
                if (project.tasks.findByName("jar") != null) {
                    dependencies {
                        selfRunTime(project)
                    }
                }
                intTestImage.into("bin") {
                    from({ project(":launcher").tasks.getByName("startScripts").outputs.files })
                    fileMode = "0755".toInt(8)
                }

                val runtimeClasspathConfigurations = rootProject.configurations.getByName("coreRuntime") +
                    rootProject.configurations.getByName("coreRuntimeExtensions") +
                    selfRunTime +
                    partialDistribution

                val libsThisProjectDoesNotUse = rootProject.configurations.getByName("runtime") +
                    rootProject.configurations.getByName("gradlePlugins") -
                    runtimeClasspathConfigurations

                intTestImage.into("lib") {
                    from(rootProject.configurations.getByName("runtime") - libsThisProjectDoesNotUse)
                    into("plugins") {
                        from(rootProject.configurations.getByName("gradlePlugins") -
                            rootProject.configurations.getByName("runtime") -
                            libsThisProjectDoesNotUse)
                    }
                }

                intTestImage.into("samples") {
                    from({(project(":docs").extra.get("outputs") as Map<*, *>)["samples"]})
                }

                intTestImage.doLast {
                    ant.withGroovyBuilder {
                        "chmod"("dir" to "${intTestImage.destinationDir}/bin", "perm" to "ugo+rx", "includes" to "**/*")
                    }
                }
            }
        }
    }
}
