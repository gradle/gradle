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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask
import org.gradle.api.tasks.diagnostics.ProjectReportTask
import org.gradle.api.tasks.diagnostics.PropertyReportTask
import org.gradle.api.tasks.diagnostics.TaskReportTask
import org.gradle.configuration.Help
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class HelpTasksPluginSpec extends AbstractProjectBuilderSpec {

    def "adds help tasks"() {
        when:
        project.pluginManager.apply(HelpTasksPlugin)

        then:
        hasHelpTask(ProjectInternal.HELP_TASK, Help)
        hasHelpTask(HelpTasksPlugin.DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask)
        hasHelpTask(HelpTasksPlugin.DEPENDENCIES_TASK, DependencyReportTask)
        hasHelpTask(ProjectInternal.PROJECTS_TASK, ProjectReportTask)
        hasHelpTask(ProjectInternal.TASKS_TASK, TaskReportTask)
        hasHelpTask(HelpTasksPlugin.PROPERTIES_TASK, PropertyReportTask)
        hasHelpTask(HelpTasksPlugin.OUTGOING_VARIANTS_TASK, OutgoingVariantsReportTask)
        hasHelpTask(BuildEnvironmentReportTask.TASK_NAME, BuildEnvironmentReportTask)
    }

    def "tasks description reflects whether project has sub-projects or not"() {
        given:
        def child = TestUtil.createChildProject(project, "child")

        when:
        project.pluginManager.apply(HelpTasksPlugin)
        child.pluginManager.apply(HelpTasksPlugin)

        then:
        project.tasks[ProjectInternal.TASKS_TASK].description == "Displays the tasks runnable from root project 'test-project' (some of the displayed tasks may belong to subprojects)."
        child.tasks[ProjectInternal.TASKS_TASK].description == "Displays the tasks runnable from project ':child'."
    }

    private hasHelpTask(String name, Class type) {
        def task = project.tasks.getByName(name)
        assert type.isInstance(task)
        assert task.group == HelpTasksPlugin.HELP_GROUP
        assert task.impliesSubProjects
        if (type != Help.class) {
            assert task.description.contains(project.name)
        }
        return task
    }
}
