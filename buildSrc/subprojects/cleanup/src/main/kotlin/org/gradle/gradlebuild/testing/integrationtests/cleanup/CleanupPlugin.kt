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
package org.gradle.gradlebuild.testing.integrationtests.cleanup

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.kotlin.dsl.*
import org.gradle.util.GradleVersion


class CleanupPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.register("cleanUpCaches", CleanUpCaches::class) {
            dependsOn(":createBuildReceipt")
            version.set(GradleVersion.version(project.version.toString()))
            homeDir.set(layout.projectDirectory.dir("intTestHomeDir"))
        }
        val tracker = gradle.sharedServices.registerIfAbsent("daemonTracker", DaemonTracker::class.java) {
            parameters.gradleHomeDir.fileValue(gradle.gradleHomeDir)
            parameters.rootProjectDir.fileValue(rootProject.projectDir)
        }
        extensions.create("cleanup", CleanupExtension::class.java, tracker)
        tasks.register("cleanUpDaemons", CleanUpDaemons::class) {
            this.tracker.set(tracker)
        }

        val killExistingProcessesStartedByGradle = tasks.register("killExistingProcessesStartedByGradle", KillLeakingJavaProcesses::class) {
            this.tracker.set(tracker)
        }

        if (BuildEnvironment.isCiServer) {
            tasks {
                val cleanTask = getByName("clean") {
                    // TODO: See https://github.com/gradle/gradle-native/issues/718
                    dependsOn(killExistingProcessesStartedByGradle)
                }
                subprojects {
                    this.tasks.configureEach {
                        mustRunAfter(killExistingProcessesStartedByGradle)

                        // Workaround for https://github.com/gradle/gradle/issues/2488
                        if (this != cleanTask) {
                            mustRunAfter(cleanTask)
                        }
                    }
                }
            }
        }
    }
}
