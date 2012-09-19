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
package org.gradle.configuration

import org.gradle.api.DefaultTask
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.diagnostics.ProjectReportTask
import org.gradle.api.tasks.diagnostics.PropertyReportTask
import org.gradle.api.tasks.diagnostics.TaskReportTask
import org.gradle.util.HelperUtil
import spock.lang.Specification

class ImplicitTasksConfigurerTest extends Specification {
    private final ImplicitTasksConfigurer configurer = Spy(ImplicitTasksConfigurer)
    private final ProjectInternal project = HelperUtil.createRootProject()

    def addsImplicitTasksToProject() {
        given:
        1 * configurer.applyPlugins(project) >> {}

        when:
        configurer.execute(project)

        then:
        project.implicitTasks['help'] instanceof DefaultTask
        project.implicitTasks['projects'] instanceof ProjectReportTask
        project.implicitTasks['tasks'] instanceof TaskReportTask
        project.implicitTasks['properties'] instanceof PropertyReportTask
    }
}
