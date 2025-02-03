/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.WrapUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.TestUtil.createChildProject

class AbstractReportTaskTest extends Specification {
    def generator = Mock(Runnable)
    def renderer = Mock(ReportRenderer)
    private TestReportTask task

    @Rule
    public TestNameTestDirectoryProvider tmpDir = TestNameTestDirectoryProvider.newInstance(getClass())
    private ProjectInternal project = TestUtil.create(tmpDir).rootProject()
    private ProjectDetails projectDetails = ProjectDetails.of(project)

    def setup() throws Exception {
        task = TestUtil.createTask(TestReportTask.class, project)
        task.setGenerator(generator)
        task.setRenderer(renderer)
        task.setProjects(WrapUtil.<Project>toSet(project))
    }

    def completesRendererAtEndOfGeneration() {
        when:
        task.generate()

        then:
        1 * renderer.setClientMetaData(_)
        1 * renderer.setOutput(_ as StyledTextOutput)
        1 * renderer.startProject(projectDetails)
        1 * generator.run()
        1 * renderer.completeProject(projectDetails)
        1 * renderer.complete()
    }

    def setsOutputFileNameOnRendererBeforeGeneration() {
        final File file = tmpDir.getTestDirectory().file("report.txt")

        when:
        task.setOutputFile(file)
        task.generate()

        then:
        1 * renderer.setClientMetaData(_)
        1 * renderer.setOutputFile(file)
        1 * renderer.startProject(projectDetails)
        1 * generator.run()
        1 * renderer.completeProject(projectDetails)
        1 * renderer.complete()
    }

    def passesEachProjectToRenderer() {
        final Project child1 = createChildProject(project, "child1")
        final Project child2 = createChildProject(project, "child2")

        when:
        task.setProjects(project.getAllprojects())
        task.generate()

        then:
        1 * renderer.setClientMetaData(_)
        1 * renderer.setOutput(_ as StyledTextOutput)
        [project, child1, child2].each {
            final ProjectDetails p = ProjectDetails.of(it)
            1 * renderer.startProject(p)
            1 * generator.run()
            1 * renderer.completeProject(p)
        }
        1 * renderer.complete()
    }

    @SuppressWarnings('GrDeprecatedAPIUsage')
    static class TestReportTask extends AbstractReportTask {
        private Runnable generator
        private ReportRenderer renderer

        void setGenerator(Runnable generator) {
            this.generator = generator
        }

        ReportRenderer getRenderer() {
            return renderer
        }

        void setRenderer(ReportRenderer renderer) {
            this.renderer = renderer
        }

        void generate(Project project) throws IOException {
            generator.run()
        }
    }
}
