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

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.AbstractSpockTaskTest
import org.gradle.logging.internal.TestStyledTextOutput
import org.gradle.util.HelperUtil
import org.gradle.api.Project

class ProjectReportTaskTest extends AbstractSpockTaskTest {
    private final ProjectReportTask task = createTask(ProjectReportTask)

    @Override
    AbstractTask getTask() {
        return task
    }

    def rendersReport() {
        project.description = 'this is the root project'
        Project child1 = HelperUtil.createChildProject(project, "child1")
        child1.description = 'this is a subproject'
        HelperUtil.createChildProject(child1, "child1")
        HelperUtil.createChildProject(project, "child2")
        task.textOutput = new TestStyledTextOutput()

        when:
        task.listProjects()

        then:
        task.textOutput.value == toNative('''
------------------------------------------------------------
Build 'test'
------------------------------------------------------------

Root project 'test' - this is the root project
+--- Project ':child1' - this is a subproject
|    \\--- Project ':child1:child1'
\\--- Project ':child2'
''')
    }

    def String toNative(String value) {
        return value.replaceAll('\n', System.getProperty('line.separator'))
    }
}
