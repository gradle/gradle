/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.gradle.api.Project
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class ProjectReportTaskTest extends AbstractProjectBuilderSpec {
    ProjectReportTask task
    final TestStyledTextOutput output = new TestStyledTextOutput().ignoreStyle()

    def setup() {
        task = TestUtil.createTask(ProjectReportTask, project)
        task.renderer.output = output
    }

    def rendersReportForRootProjectWithChildren() {
        project.description = 'this is the root project'
        Project child1 = TestUtil.createChildProject(project, "child1")
        child1.description = 'this is a subproject'
        TestUtil.createChildProject(child1, "child1")
        TestUtil.createChildProject(project, "child2")

        when:
        def model = task.calculateReportModelFor(project)
        task.generateReportFor(model.project, model)

        then:
        output.value == '''Root project 'test-project' - this is the root project
+--- Project ':child1' - this is a subproject
|    \\--- Project ':child1:child1'
\\--- Project ':child2'

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :child1:tasks
'''
    }

    def rendersReportForRootProjectWithNoChildren() {
        project.description = 'this is the root project'

        when:
        def model = task.calculateReportModelFor(project)
        task.generateReportFor(model.project, model)

        then:
        output.value == '''Root project 'test-project' - this is the root project
No sub-projects

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :tasks
'''
    }

    def rendersReportForNonRootProjectWithNoChildren() {
        Project child1 = TestUtil.createChildProject(project, "child1")

        when:
        def model = task.calculateReportModelFor(child1)
        task.generateReportFor(model.project, model)

        then:
        output.value == '''Project ':child1'
No sub-projects

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :child1:tasks

To see a list of all the projects in this build, run gradle :projects
'''
    }
}
