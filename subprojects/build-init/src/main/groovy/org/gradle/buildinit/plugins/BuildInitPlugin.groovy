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

package org.gradle.buildinit.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.buildinit.tasks.InitBuild

@Incubating
class BuildInitPlugin implements Plugin<Project> {
    public static final String INIT_BUILD_TASK_NAME = "init"
    public static final String GROUP = 'Build Setup'

    void apply(Project project) {
        Task init = project.tasks.create(INIT_BUILD_TASK_NAME, InitBuild)
        init.group = GROUP
        init.description = "Initializes a new Gradle build. [incubating]"
        Closure setupCanBeSkipped = {
            if (project.file("build.gradle").exists()) {
                return ("The build file 'build.gradle' already exists. Skipping build initialization.")
            }
            if (project.buildFile?.exists()) {
                return ("The build file '$project.buildFile.name' already exists. Skipping build initialization.")
            }
            if (project.file("settings.gradle").exists()) {
                return ("The settings file 'settings.gradle' already exists. Skipping build initialization.")
            }
            if (project.subprojects.size() > 0) {
                return ("This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.")
            }
            return null
        }
        init.onlyIf {
            def skippedMsg = setupCanBeSkipped()
            if (skippedMsg) {
                project.logger.warn skippedMsg
                return false
            }
            return true
        }

        if (!setupCanBeSkipped()) {
            init.dependsOn("wrapper")
        }
    }
}