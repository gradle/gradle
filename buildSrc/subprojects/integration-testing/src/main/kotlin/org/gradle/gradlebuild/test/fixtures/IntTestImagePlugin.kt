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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.gradle.gradlebuild.test.integrationtests.DistributionTest

import org.gradle.kotlin.dsl.*
import useAllDistribution
import java.util.concurrent.Callable


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
open class IntTestImagePlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val intTestImage by tasks.creating(Sync::class) {
            group = "Verification"
            into(file("$buildDir/integ test"))
        }

        tasks.configureEachLater(DistributionTest::class.java) {
            dependsOn(intTestImage)
        }

        val partialDistribution by configurations.creating {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            }
            isCanBeResolved = true
            isCanBeConsumed = false
        }

        if (useAllDistribution) {
            val unpackAllDistribution by tasks.creating(Sync::class) {
                dependsOn(":distributions:allZip")
                from(Callable { zipTree(rootProject.project("distributions").tasks.getByName<Zip>("allZip").archivePath) })
                into("$buildDir/tmp/unpacked-all-distribution")
            }
            val unpackedPath = "${unpackAllDistribution.destinationDir}/gradle-$version"
            intTestImage.apply {
                dependsOn(unpackAllDistribution)
                from(unpackedPath)
            }
        } else {
            val selfRuntime by configurations.creating {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                }
                isCanBeResolved = true
                isCanBeConsumed = false
            }
            afterEvaluate {
                if (project.tasks.findByName("jar") != null) {
                    dependencies {
                        selfRuntime(this@afterEvaluate)
                    }
                }
                intTestImage.apply {
                    into("bin") {
                        from(Callable { project(":launcher").tasks.getByName("startScripts").outputs.files })
                        Integer.parseInt("0755", 8)
                    }

                    val runtimeClasspathConfigurations = (rootProject.configurations["coreRuntime"]
                        + rootProject.configurations["coreRuntimeExtensions"]
                        + selfRuntime
                        + partialDistribution)

                    val libsThisProjectDoesNotUse = (rootProject.configurations["runtime"] + rootProject.configurations["gradlePlugins"]) - runtimeClasspathConfigurations

                    into("lib") {
                        from(rootProject.configurations["runtime"] - libsThisProjectDoesNotUse)
                        into("plugins") {
                            from(rootProject.configurations["gradlePlugins"] - rootProject.configurations["runtime"] - libsThisProjectDoesNotUse)
                        }
                    }

                    into("samples") {
                        from(Callable { (project(":docs").extra.get("outputs") as Map<String, FileCollection>)["samples"] })
                    }

                    doLast {
                        ant.withGroovyBuilder {
                            "chmod"("dir" to "$destinationDir/bin", "perm" to "ugo+rx", "includes" to "**/*")
                        }
                    }
                }
            }
        }
    }
}
