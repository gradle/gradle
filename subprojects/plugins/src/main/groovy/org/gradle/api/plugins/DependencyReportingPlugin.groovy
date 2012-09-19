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

package org.gradle.api.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.configuration.ImplicitTasksConfigurer

/**
 * by Szczepan Faber, created at: 9/5/12
 */
@Incubating
class DependencyReportingPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.task("dependencyInsight", type: DependencyInsightReportTask) { task ->
            description = "Displays the insight into a specific dependency in $project."
            group = ImplicitTasksConfigurer.HELP_GROUP

            project.plugins.withType(JavaPlugin) {
                task.configuration = project.configurations.getByName("compile")
            }
        }

        //for compatibility reasons, I'm adding the dependencies task as an implicit task
        //this way name-matching execution will not trigger dependencies task for every project in a multi project build
        //TODO SF add coverage for this functionality
        project.implicitTasks.add(name: "dependencies", type: DependencyReportTask) {
            description = "Displays all dependencies declared in $project."
            group = ImplicitTasksConfigurer.HELP_GROUP
        }
    }
}