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
package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Matchers;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class ProjectDefaultsBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ProjectInternal project = context.mock(ProjectInternal.class, "[project]");
    private final GradleInternal gradle = context.mock(GradleInternal.class);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(gradle).getDefaultProject();
            will(returnValue(project));
        }});
    }
    
    @Test public void usesProjectDefaultTasksFromProject() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(toList("a", "b")));
        }});

        TestProjectDefaultsBuildExecuter executer = new TestProjectDefaultsBuildExecuter();
        executer.select(gradle);
        assertThat(executer.actualDelegate, Matchers.reflectionEquals((Object) new TaskNameResolvingBuildExecuter(toList("a", "b"))));
    }

    @Test public void createsDescription() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(toList("a", "b")));
        }});

        TestProjectDefaultsBuildExecuter executer = new TestProjectDefaultsBuildExecuter();
        executer.select(gradle);
        assertThat(executer.getDisplayName(), equalTo("project default tasks 'a', 'b'"));
    }

    @Test public void failsWhenNoProjectDefaultTasksSpecified() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(toList()));
        }});

        BuildExecuter executer = new ProjectDefaultsBuildExecuter();
        try {
            executer.select(gradle);
            fail();
        } catch (TaskSelectionException e) {
            assertThat(e.getMessage(), equalTo("No tasks have been specified and [project] has not defined any default tasks."));
        }
    }

    private static class TestProjectDefaultsBuildExecuter extends ProjectDefaultsBuildExecuter {
        private BuildExecuter actualDelegate;

        @Override
        protected void setDelegate(BuildExecuter delegate) {
            actualDelegate = delegate;
            super.setDelegate(new BuildExecuter() {
                public void select(GradleInternal gradle) {
                }

                public String getDisplayName() {
                    return null;
                }

                public void execute() {
                }
            });
        }
    }
}
