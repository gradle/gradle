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

import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.buildsetup.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildsetup.plugins.internal.ProjectLayoutSetupRegistryFactory
import org.gradle.buildsetup.tasks.ConvertMaven2Gradle
import org.gradle.buildsetup.tasks.GenerateBuildFile
import org.gradle.buildsetup.tasks.GenerateSettingsFile
import org.gradle.buildsetup.tasks.ProjectLayoutSetup
import org.gradle.buildsetup.tasks.SetupBuild
import org.gradle.execution.TaskSelector
import org.gradle.execution.commandline.CommandLineTaskParser

@Incubating
class BuildSetupPlugin implements Plugin<Project> {
    public static final String SETUP_BUILD_TASK_NAME = "setupBuild"
    public static final String GROUP = 'Build Setup'
    private Project project

    void apply(Project project) {
        this.project = project
        Task setupBuild = project.getTasks().create(SETUP_BUILD_TASK_NAME, SetupBuild);
        setupBuild.group = GROUP
        setupBuild.description = "Initializes a new Gradle build. [incubating]"
        boolean buildSetupWillBeSkipped = configureBuildSetupTask(project, setupBuild)
        if (!buildSetupWillBeSkipped) {
            configureBuildSetupTaskByCommandLineOptions()
            createFurtherSetupTasks(project, setupBuild)
            configureSetupTasks(project, setupBuild)
        }
    }


    void configureBuildSetupTaskByCommandLineOptions() {
        CommandLineTaskParser commandLineTaskParser = new CommandLineTaskParser()
        commandLineTaskParser.parseTasks(project.gradle.startParameter.taskNames, new TaskSelector(project.gradle))
    }

    boolean configureBuildSetupTask(Project project, Task setupBuildTask) {
        if (project.file("build.gradle").exists()) {
            setupBuildTask.doLast {
                logger.warn("The build file 'build.gradle' already exists. Skipping build initialization.")
            }
            return true
        }
        if (project.buildFile?.exists()) {
            setupBuildTask.doLast {
                logger.warn("The build file '$project.buildFile.name' already exists. Skipping build initialization.")
            }
            return true
        }
        if (project.file("settings.gradle").exists()) {
            setupBuildTask.doLast {
                logger.warn("The settings file 'settings.gradle' already exists. Skipping build initialization.")
            }
            return true
        }
        if (project.subprojects.size() > 0) {
            setupBuildTask.doLast {
                logger.warn("This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.")
            }
            return true
        }
        return false
    }

    def createFurtherSetupTasks(Project project, SetupBuild setupBuild) {
        setupBuild.dependsOn("wrapper")

        if (setupBuild.type == null && project.file("pom.xml").exists()) {
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
    }

    void configureSetupTasks(Project project, SetupBuild setupBuild) {
        String setupType = setupBuild.type
        if (setupType != null) {
            ProjectLayoutSetupRegistryFactory projectLayoutRegistryFactory = new ProjectLayoutSetupRegistryFactory(project);
            ProjectLayoutSetupRegistry projectLayoutRegistry = projectLayoutRegistryFactory.createProjectLayoutSetupRegistry()
            ProjectSetupDescriptor descriptor = projectLayoutRegistry.get(setupType);
            if(descriptor==null){
                throw new GradleException("Declared setup-type '${setupType}' is not supported. ")
            }
            ProjectLayoutSetup createProjectLayout = project.task("setupProjectLayout", type: ProjectLayoutSetup)
            createProjectLayout.projectSetupDescriptor = descriptor
            setupBuild.dependsOn createProjectLayout

            URL setupTypeBuildTemplate = GenerateBuildFile.class.getResource("/org/gradle/buildsetup/tasks/templates/${setupType}-build.gradle.template");
            if (setupTypeBuildTemplate != null) {
                project.generateBuildFile.templateURL = setupTypeBuildTemplate
            }

            URL setupTypeBuildSettings = GenerateSettingsFile.class.getResource("/org/gradle/buildsetup/tasks/templates/${setupType}-settings.gradle.template");
            if (setupTypeBuildSettings != null) {
                project.generateSettingsFile.templateURL = setupTypeBuildTemplate
            }

        }
    }
}