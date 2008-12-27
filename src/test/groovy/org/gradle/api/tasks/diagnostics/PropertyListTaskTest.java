/*
 * Copyright 2007, 2008 the original author or authors.
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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.internal.project.ProjectInternal;

@RunWith(JMock.class)
public class PropertyListTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ProjectInternal project;
    private PropertyListTask task;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        project = context.mock(ProjectInternal.class);

        context.checking(new Expectations(){{
            allowing(project).getRootProject();
            will(returnValue(project));
            allowing(project).absolutePath("list");
            will(returnValue(":path"));
        }});

        task = new PropertyListTask(project, "list");
    }

    @Test
    public void isDagNeutral() {
        assertTrue(task.isDagNeutral());
    }
}
