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
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
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
        val intTestImage = tasks.register("intTestImage", Sync::class) {
            group = "Verification"
            into(file("$buildDir/integ test"))
        }

        tasks.withType<DistributionTest>().configureEach {
            dependsOn(intTestImage)
        }

        val partialDistribution by configurations.creating {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
            }
            isCanBeResolved = true
            isCanBeConsumed = false
        }

        val gradleRuntimeSource by configurations.creating {
            isVisible = false
            isCanBeResolved = false
            isCanBeConsumed = false
        }
        val coreGradleRuntimeExtensions by configurations.creating {
            extendsFrom(gradleRuntimeSource)
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                attribute(Attribute.of("org.gradle.api", String::class.java), "core-ext")
            }
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
        }
        val coreGradleRuntime by configurations.creating {
            extendsFrom(gradleRuntimeSource)
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                attribute(Attribute.of("org.gradle.api", String::class.java), "core")
            }
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
        }
        val builtInGradlePlugins by configurations.creating {
            extendsFrom(gradleRuntimeSource)
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
                attribute(Attribute.of("org.gradle.api", String::class.java), "plugins")
            }
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
        }
        val gradleDocumentation by configurations.creating {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "docs"))
            }
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
        }
        val gradleScripts by configurations.creating {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "start-scripts"))
            }
            isVisible = false
            isCanBeResolved = true
            isCanBeConsumed = false
        }
        // TODO: Model these as publications of different types of distributions, collapse the number of variants exposed by the root project
        // and eliminate duplication with distributions.gradle
        dependencies {
            gradleRuntimeSource(project(":"))
            gradleRuntimeSource(project(":apiMetadata"))
            gradleDocumentation(project(":docs"))
            gradleScripts(project(":launcher"))
        }

        if (useAllDistribution) {
            val unpackedPath = layout.buildDirectory.dir("tmp/unpacked-all-distribution")

            val unpackAllDistribution = tasks.register("unpackAllDistribution", Sync::class) {
                dependsOn(":distributions:allZip")
                // TODO: This should be modelled as a publication
                from(Callable {
                    val distributionsProject = rootProject.project("distributions")
                    val allZip = distributionsProject.tasks.getByName<Zip>("allZip")
                    zipTree(allZip.archiveFile)
                })
                into(unpackedPath)
            }

            intTestImage.configure {
                dependsOn(unpackAllDistribution)
                from(unpackedPath.get().dir("gradle-$version"))
            }
        } else {
            afterEvaluate {
                if (!project.configurations["default"].allArtifacts.isEmpty()) {
                    dependencies {
                        partialDistribution(this@afterEvaluate)
                    }
                }

                intTestImage.configure {
                    into("bin") {
                        from(gradleScripts)
                        fileMode = Integer.parseInt("0755", 8)
                    }

                    val runtimeClasspathConfigurations = (coreGradleRuntimeExtensions + partialDistribution)

                    val libsThisProjectDoesNotUse = (coreGradleRuntime + builtInGradlePlugins) - runtimeClasspathConfigurations

                    into("lib") {
                        from(coreGradleRuntime - libsThisProjectDoesNotUse)
                        into("plugins") {
                            from(builtInGradlePlugins - coreGradleRuntime - libsThisProjectDoesNotUse)
                        }
                    }

                    preserve {
                        include("samples/**")
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
