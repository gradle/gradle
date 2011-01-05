/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.HelperUtil;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.gradle.util.HelperUtil.createChildProject;
import static org.gradle.util.HelperUtil.createRootProject;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(JMock.class)
public class AbstractReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultProject project = createRootProject();
    private Runnable generator;
    private TestReportTask task;
    private ReportRenderer renderer;
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        generator = context.mock(Runnable.class);
        renderer = context.mock(ReportRenderer.class);
        task = HelperUtil.createTask(TestReportTask.class, project);
        task.setGenerator(generator);
        task.setRenderer(renderer);
        task.setProjects(WrapUtil.<Project>toSet(project));
    }

    @Test
    public void completesRendererAtEndOfGeneration() throws IOException {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("sequence");
            one(renderer).setOutput((StyledTextOutput) with(notNullValue()));
            inSequence(sequence);
            one(renderer).startProject(project);
            inSequence(sequence);
            one(generator).run();
            inSequence(sequence);
            one(renderer).completeProject(project);
            inSequence(sequence);
            one(renderer).complete();
            inSequence(sequence);
        }});

        task.generate();
    }

    @Test
    public void setsOutputFileNameOnRendererBeforeGeneration() throws IOException {
        final File file = tmpDir.getDir().file("report.txt");

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("sequence");
            one(renderer).setOutputFile(file);
            inSequence(sequence);
            one(renderer).startProject(project);
            inSequence(sequence);
            one(generator).run();
            inSequence(sequence);
            one(renderer).completeProject(project);
            inSequence(sequence);
            one(renderer).complete();
            inSequence(sequence);
        }});

        task.setOutputFile(file);
        task.generate();
    }

    @Test
    public void passesEachProjectToRenderer() throws IOException {
        final Project child1 = createChildProject(project, "child1");
        final Project child2 = createChildProject(project, "child2");
        task.setProjects(project.getAllprojects());
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(renderer).setOutput((StyledTextOutput) with(notNullValue()));
            inSequence(sequence);
            one(renderer).startProject(project);
            inSequence(sequence);
            one(generator).run();
            inSequence(sequence);
            one(renderer).completeProject(project);
            inSequence(sequence);
            one(renderer).startProject(child1);
            inSequence(sequence);
            one(generator).run();
            inSequence(sequence);
            one(renderer).completeProject(child1);
            inSequence(sequence);
            one(renderer).startProject(child2);
            inSequence(sequence);
            one(generator).run();
            inSequence(sequence);
            one(renderer).completeProject(child2);
            inSequence(sequence);
            one(renderer).complete();
            inSequence(sequence);
        }});

        task.generate();
    }

    public static class TestReportTask extends AbstractReportTask {
        private Runnable generator;
        private ReportRenderer renderer;

        public void setGenerator(Runnable generator) {
            this.generator = generator;
        }

        public ReportRenderer getRenderer() {
            return renderer;
        }

        public void setRenderer(ReportRenderer renderer) {
            this.renderer = renderer;
        }

        public void generate(Project project) throws IOException {
            generator.run();
        }
    }
}
