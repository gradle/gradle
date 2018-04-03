/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.gradlebuild.buildquality

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.gradlebuild.ProjectGroups

import org.gradle.kotlin.dsl.*

import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension


open class DependencyVulnerabilitiesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        configure(projectsIncludedInDistribution()) {
            apply {
                plugin("org.owasp.dependencycheck")
            }

            configure<DependencyCheckExtension> {
                failBuildOnCVSS = 8F // 10 is the maximum
                skipConfigurations = listOf("jmh")
            }
        }
    }

    private
    fun Project.projectsIncludedInDistribution(): List<Project> {
        return subprojects.filter { prj ->
            !ProjectGroups.excludedFromVulnerabilityCheck.contains(prj.path)
        }
    }
}


