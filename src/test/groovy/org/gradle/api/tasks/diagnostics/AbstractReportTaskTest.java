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
import static org.gradle.util.HelperUtil.*;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(JMock.class)
public class AbstractReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultProject project = createRootProject();
    private Runnable generator;
    private TestReportTask task;
    private ProjectReportRenderer renderer;

    @Before
    public void setUp() throws Exception {
        generator = context.mock(Runnable.class);
        renderer = context.mock(ProjectReportRenderer.class);
        task = new TestReportTask(project, "name", generator, renderer);
    }

    @Test
    public void completesRendererAtEndOfGeneration() throws IOException {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("sequence");
            one(renderer).startProject(project);
            inSequence(sequence);
            one(generator).run();
            inSequence(sequence);
            one(renderer).completeProject(project);
            inSequence(sequence);
            one(renderer).complete();
            inSequence(sequence);
        }});

        task.execute();
    }

    @Test
    public void setsOutputFileNameOnRendererBeforeGeneration() throws IOException {
        final File file = new File(getTestDir(), "report.txt");

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
        task.execute();
    }

    @Test
    public void passesEachProjectToRenderer() throws IOException {
        final Project child1 = createChildProject(project, "child1");
        final Project child2 = createChildProject(project, "child2");

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

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

        task.execute();
    }

    @Test
    public void createsMissingOutputDirectory() throws IOException {
        final File file = new File(getTestDir(), "missing/missing.txt");
        assertFalse(file.getParentFile().isDirectory());

        context.checking(new Expectations() {{
            one(renderer).setOutputFile(file);
            one(renderer).startProject(project);
            one(generator).run();
            one(renderer).completeProject(project);
            one(renderer).complete();
        }});

        task.setOutputFile(file);
        task.execute();

        assertTrue(file.getParentFile().isDirectory());
    }

    private class TestReportTask extends AbstractReportTask {
        private Runnable generator;
        private ProjectReportRenderer renderer;

        public TestReportTask(Project project, String name, Runnable generator, ProjectReportRenderer renderer) {
            super(project, name);
            this.generator = generator;
            this.renderer = renderer;
        }

        public ProjectReportRenderer getRenderer() {
            return renderer;
        }

        public void generate(Project project) throws IOException {
            generator.run();
        }
    }
}
