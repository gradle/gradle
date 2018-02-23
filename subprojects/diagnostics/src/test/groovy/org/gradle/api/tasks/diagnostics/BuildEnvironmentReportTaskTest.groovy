/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class BuildEnvironmentReportTaskTest extends AbstractProjectBuilderSpec {
    private ProjectInternal project = TestUtil.createRootProject(temporaryFolder.testDirectory)
    private BuildEnvironmentReportTask task = TestUtil.createTask(BuildEnvironmentReportTask.class, project)
    private DependencyReportRenderer renderer = Mock(DependencyReportRenderer)
    private Configuration conf1 = project.buildscript.configurations.create("conf1")
    private Configuration conf2 = project.buildscript.configurations.create("conf2")
    private Configuration classpath = project.buildscript.configurations.getByName("classpath")

    def "renders only classpath build script configuration"() {
        when:
        task.setRenderer(renderer)
        task.generate()

        then: 1 * renderer.startConfiguration(classpath)
        then: 1 * renderer.render(classpath)
        then: 1 * renderer.completeConfiguration(classpath)

        then: 0 * renderer.startConfiguration(conf1)
        then: 0 * renderer.render(conf1)
        then: 0 * renderer.completeConfiguration(conf1)

        then: 0 * renderer.startConfiguration(conf2)
        then: 0 * renderer.render(conf2)
        then: 0 * renderer.completeConfiguration(conf2)
    }
}
