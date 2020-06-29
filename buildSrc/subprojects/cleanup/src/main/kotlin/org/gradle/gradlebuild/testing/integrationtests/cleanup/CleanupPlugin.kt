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

import gradlebuild.ModuleIdentityPlugin
import gradlebuild.basics.BuildEnvironment
import gradlebuild.identity.extension.ModuleIdentityExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.kotlin.dsl.*


class CleanupPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        apply<BasePlugin>()
        apply<ModuleIdentityPlugin>() // Would be nice to avoid this in the root project, and instead apply a 'clean' Plugin to each subproject
        tasks.register<CleanUpCaches>("cleanUpCaches") {
            version.set(project.the<ModuleIdentityExtension>().version)
            homeDir.set(layout.projectDirectory.dir("intTestHomeDir"))
        }
        val tracker = gradle.sharedServices.registerIfAbsent("daemonTracker", DaemonTracker::class.java) {
            parameters.gradleHomeDir.fileValue(gradle.gradleHomeDir)
            parameters.rootProjectDir.fileValue(rootProject.projectDir)
        }
        extensions.create("cleanup", CleanupExtension::class.java, tracker)
        tasks.register<CleanUpDaemons>("cleanUpDaemons") {
            this.tracker.set(tracker)
        }

        val killExistingProcessesStartedByGradle = tasks.register<KillLeakingJavaProcesses>("killExistingProcessesStartedByGradle") {
            this.tracker.set(tracker)
        }

        // TODO find another solution here to avoid reaching into another project. Maybe the CI job should do 'killExistingProcessesStartedByGradle' first directly.
        if (BuildEnvironment.isCiServer) {
            tasks {
                val cleanTask = named("clean") {
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
