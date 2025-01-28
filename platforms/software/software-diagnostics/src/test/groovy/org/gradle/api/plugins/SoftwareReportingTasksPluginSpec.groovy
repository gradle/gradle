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


import org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.diagnostics.OutgoingVariantsReportTask
import org.gradle.api.tasks.diagnostics.ResolvableConfigurationsReportTask
import org.gradle.configuration.Help
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class SoftwareReportingTasksPluginSpec extends AbstractProjectBuilderSpec {

    def "adds reporting tasks"() {
        when:
        project.pluginManager.apply(SoftwareReportingTasksPlugin)

        then:
        hasHelpTask(HelpTasksPlugin.DEPENDENCY_INSIGHT_TASK, DependencyInsightReportTask)
        hasHelpTask(HelpTasksPlugin.DEPENDENCIES_TASK, DependencyReportTask)
        hasHelpTask(HelpTasksPlugin.OUTGOING_VARIANTS_TASK, OutgoingVariantsReportTask)
        hasHelpTask(HelpTasksPlugin.RESOLVABLE_CONFIGURATIONS_TASK, ResolvableConfigurationsReportTask)
        hasHelpTask(BuildEnvironmentReportTask.TASK_NAME, BuildEnvironmentReportTask)
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
