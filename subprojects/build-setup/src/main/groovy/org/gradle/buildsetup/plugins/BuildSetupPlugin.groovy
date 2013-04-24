/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.buildsetup.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.buildsetup.tasks.ConvertMaven2Gradle
import org.gradle.buildsetup.tasks.GenerateBuildFile
import org.gradle.buildsetup.tasks.GenerateSettingsFile

@Incubating
class BuildSetupPlugin implements Plugin<Project> {
    public static final String SETUP_BUILD_TASK_NAME = "setupBuild"
    public static final String GROUP = 'Build Setup'

    void apply(Project project) {
        Task setupBuild = project.getTasks().create(SETUP_BUILD_TASK_NAME);
        setupBuild.group = GROUP
        setupBuild.description = "Initializes a new Gradle build. [incubating]"
        boolean furtherTasksRequired = configureBuildSetupTask(project, setupBuild)
        if (furtherTasksRequired) {
            configureFurtherSetupActions(project, setupBuild)
        }
    }

    boolean configureBuildSetupTask(Project project, Task setupBuildTask) {
        if (project.file("build.gradle").exists()) {
            setupBuildTask.doLast {
                logger.warn("The build file 'build.gradle' already exists. Skipping build initialization.")
            }
            return false
        }
        if (project.buildFile?.exists()) {
            setupBuildTask.doLast {
                logger.warn("The build file '$project.buildFile.name' already exists. Skipping build initialization.")
            }
            return false
        }
        if (project.file("settings.gradle").exists()) {
            setupBuildTask.doLast {
                logger.warn("The settings file 'settings.gradle' already exists. Skipping build initialization.")
            }
            return false
        }
        if (project.subprojects.size() > 0) {
            setupBuildTask.doLast {
                logger.warn("This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.")
            }
            return false
        }
        return true
    }

    def configureFurtherSetupActions(Project project, Task setupBuild) {
        if (project.file("pom.xml").exists()) {
            def maven2Gradle = project.task("maven2Gradle", type: ConvertMaven2Gradle) {
                description = 'Generates a Gradle build from a Maven POM. [incubating]'
            }
            setupBuild.dependsOn(maven2Gradle)
        } else {
            // generate empty gradle build file
            GenerateBuildFile generateBuildFile = project.task("generateBuildFile", type: GenerateBuildFile) {
                description = 'Generates a Gradle build file. [incubating]'
                buildFile = project.file("build.gradle")
            }
            // generate empty gradle settings file
            GenerateSettingsFile generateSettingsFile = project.task("generateSettingsFile", type: GenerateSettingsFile) {
                description = 'Generates a Gradle settings file. [incubating]'
                settingsFile = project.file("settings.gradle")
            }
            setupBuild.dependsOn(generateSettingsFile)
            setupBuild.dependsOn(generateBuildFile)
        }
        setupBuild.dependsOn("wrapper")
    }
}