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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.diagnostics.internal.PropertyReportRenderer;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(JMock.class)
public class PropertyReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ProjectInternal project;
    private PropertyReportTask task;
    private PropertyReportRenderer renderer;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        project = context.mock(ProjectInternal.class);
        renderer = context.mock(PropertyReportRenderer.class);

        context.checking(new Expectations() {{
            allowing(project).absoluteProjectPath("list");
            will(returnValue(":path"));
            allowing(project).getConvention();
            will(returnValue(null));
        }});

        task = HelperUtil.createTask(PropertyReportTask.class);
        task.setRenderer(renderer);
    }

    @Test
    public void passesEachProjectPropertyToRenderer() throws IOException {
        context.checking(new Expectations() {{
            one(project).getProperties();
            will(returnValue(GUtil.map("b", "value2", "a", "value1")));

            Sequence sequence = context.sequence("seq");

            one(renderer).addProperty("a", "value1");
            inSequence(sequence);

            one(renderer).addProperty("b", "value2");
            inSequence(sequence);
        }});

        task.generate(project);
    }

    @Test
    public void doesNotShowContentsOfThePropertiesProperty() throws IOException {
        context.checking(new Expectations() {{
            one(project).getProperties();
            will(returnValue(GUtil.map("prop", "value", "properties", "prop")));

            Sequence sequence = context.sequence("seq");

            one(renderer).addProperty("prop", "value");
            inSequence(sequence);
            one(renderer).addProperty("properties", "{...}");
            inSequence(sequence);
        }});

        task.generate(project);
    }
}
