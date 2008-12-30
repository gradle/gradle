/*
 * Copyright 2008 the original author or authors.
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

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.jmock.Sequence;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.Project;
import org.gradle.util.GUtil;
import static org.gradle.util.WrapUtil.*;

import java.util.Collections;

@RunWith(JMock.class)
public class PropertyReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ProjectInternal project;
    private PropertyReportTask task;
    private PropertyReportRenderer formatter;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        project = context.mock(ProjectInternal.class);
        formatter = context.mock(PropertyReportRenderer.class);

        context.checking(new Expectations() {{
            allowing(project).getRootProject();
            will(returnValue(project));
            allowing(project).absolutePath("list");
            will(returnValue(":path"));
        }});

        task = new PropertyReportTask(project, "list");
        task.setFormatter(formatter);
    }

    @Test
    public void isDagNeutral() {
        assertTrue(task.isDagNeutral());
    }

    @Test
    public void passesCurrentProjectAndEachSubProjectToFormatter() {
        final Project child1 = context.mock(Project.class, "child1");
        final Project child2 = context.mock(Project.class, "child2");

        context.checking(new Expectations() {{
            one(project).getAllprojects();
            will(returnValue(toLinkedSet(child1, project, child2)));

            allowing(project).getProperties();
            will(returnValue(Collections.emptyMap()));

            allowing(child1).getProperties();
            will(returnValue(Collections.emptyMap()));

            allowing(child2).getProperties();
            will(returnValue(Collections.emptyMap()));

            allowing(project).compareTo(child1);
            will(returnValue(-1));

            allowing(child2).compareTo(child1);
            will(returnValue(1));

            Sequence sequence = context.sequence("seq");

            one(formatter).startProject(project);
            inSequence(sequence);
            one(formatter).completeProject(project);
            inSequence(sequence);
            one(formatter).startProject(child1);
            inSequence(sequence);
            one(formatter).completeProject(child1);
            inSequence(sequence);
            one(formatter).startProject(child2);
            inSequence(sequence);
            one(formatter).completeProject(child2);
            inSequence(sequence);
            one(formatter).complete();
            inSequence(sequence);
        }});

        task.execute();
    }

    @Test
    public void passesEachPropertyToFormatter() {
        context.checking(new Expectations() {{
            one(project).getAllprojects();
            will(returnValue(toLinkedSet(project)));
            one(project).getProperties();
            will(returnValue(GUtil.map("b", "value2", "a", "value1")));

            Sequence sequence = context.sequence("seq");

            one(formatter).startProject(project);
            inSequence(sequence);
            one(formatter).addProperty("a", "value1");
            inSequence(sequence);
            one(formatter).addProperty("b", "value2");
            inSequence(sequence);
            one(formatter).completeProject(project);
            inSequence(sequence);
            one(formatter).complete();
            inSequence(sequence);
        }});

        task.execute();
    }
}
