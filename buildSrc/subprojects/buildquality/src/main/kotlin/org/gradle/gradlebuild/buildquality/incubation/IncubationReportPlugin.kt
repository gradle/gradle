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

package org.gradle.gradlebuild.buildquality.incubation

import accessors.java
import build.kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*


class IncubationReportPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val main by java.sourceSets
        val reportTask = tasks.register<IncubatingApiReportTask>("incubationReport") {
            description = "Generates a report of incubating APIS"
            title.set(project.name)
            versionFile.set(rootProject.file("version.txt"))
            releasedVersionsFile.set(rootProject.file("released-versions.json"))
            sources.from(main.java.sourceDirectories)
            htmlReportFile.set(file("$buildDir/reports/incubation/${project.name}.html"))
            textReportFile.set(file("$buildDir/reports/incubation/${project.name}.txt"))
        }
        plugins.withId("org.jetbrains.kotlin.jvm") {
            reportTask {
                sources.from(main.kotlin.sourceDirectories)
            }
        }
        tasks.named("check") { dependsOn(reportTask) }
    }
}
