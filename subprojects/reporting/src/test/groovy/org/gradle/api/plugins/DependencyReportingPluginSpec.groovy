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

import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import static org.gradle.configuration.ImplicitTasksConfigurer.*
import org.gradle.configuration.Help
import org.gradle.api.tasks.diagnostics.ProjectReportTask
import org.gradle.api.tasks.diagnostics.TaskReportTask
import org.gradle.api.tasks.diagnostics.PropertyReportTask
import org.gradle.api.tasks.diagnostics.DependencyReportTask

/**
 * by Szczepan Faber, created at: 9/5/12
 */
class DependencyReportingPluginSpec extends Specification {

    def project = new ProjectBuilder().build()

    def "adds help tasks"() {
        when:
        project.apply(plugin: 'dependency-reporting')

        then:
        hasHelpTask(HELP_TASK, Help)
        hasHelpTask(DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask)
        hasHelpTask(DEPENDENCIES_TASK, DependencyReportTask)
        hasHelpTask(PROJECTS_TASK, ProjectReportTask)
        hasHelpTask(TASKS_TASK, TaskReportTask)
        hasHelpTask(PROPERTIES_TASK, PropertyReportTask)
    }

    private void hasHelpTask(String name, Class type) {
        def task = project.implicitTasks.getByName(name)
        assert type.isInstance(task)
        assert task.group == HELP_GROUP
        if (type != Help.class) {
            assert task.description.contains(project.name)
        }
    }
}
