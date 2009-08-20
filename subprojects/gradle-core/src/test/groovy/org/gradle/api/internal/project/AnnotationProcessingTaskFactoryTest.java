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
package org.gradle.api.internal.project;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.TaskAction;
import static org.gradle.util.Matchers.*;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(JMock.class)
public class AnnotationProcessingTaskFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private final ITaskFactory delegate = context.mock(ITaskFactory.class);
    private final Project project = context.mock(Project.class);
    private final Map args = new HashMap();
    private final AnnotationProcessingTaskFactory factory = new AnnotationProcessingTaskFactory(delegate);

    @Test
    public void attachesAnActionToTaskForMethodMarkedWithExecuteAnnotation() {
        final Runnable action = context.mock(Runnable.class);
        final TestTask task = new TestTask(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            one(action).run();
        }});
        task.execute();
    }

    private void expectTaskCreated(final Task task) {
        context.checking(new Expectations() {{
            one(delegate).createTask(project, args);
            will(returnValue(task));
        }});

        assertThat(factory.createTask(project, args), sameInstance((Object) task));
    }

    @Test
    public void doesNothingToTaskWithNoExecuteAnnotations() {
        final TaskInternal task = new DefaultTask();

        expectTaskCreated(task);

        assertThat(task.getActions(), isEmpty());
    }

    @Test
    public void propagatesExceptionThrownByExecuteMethod() {
        final Runnable action = context.mock(Runnable.class);
        TestTask task = new TestTask(action);

        expectTaskCreated(task);

        final RuntimeException failure = new RuntimeException();
        context.checking(new Expectations() {{
            one(action).run();
            will(throwException(failure));
        }});

        try {
            task.getActions().get(0).execute(task);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }

    @Test
    public void canHaveMultipleMethodsWithExecuteAnnotation() {
        final Runnable action = context.mock(Runnable.class);
        TaskWithMultipleMethods task = new TaskWithMultipleMethods(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            exactly(3).of(action).run();
        }});

        task.execute();
    }

    @Test
    public void failsWhenStaticMethodHasExecuteAnnotation() {
        TaskWithStaticMethod task = new TaskWithStaticMethod();
        assertTaskCreationFails(task, "Cannot use @TaskAction annotation on static method TaskWithStaticMethod.doStuff().");
    }

    @Test
    public void failsWhenMethodWithParametersHasExecuteAnnotation() {
        TaskWithParamMethod task = new TaskWithParamMethod();
        assertTaskCreationFails(task, "Cannot use @TaskAction annotation on method TaskWithParamMethod.doStuff() as this method takes parameters.");
    }

    private void assertTaskCreationFails(Task task, String message) {
        try {
            expectTaskCreated(task);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo(message));
        }
    }

    @Test
    public void actionWorksForInheritedExecuteMethods() {
        final Runnable action = context.mock(Runnable.class);
        final TaskWithInheritedMethod task = new TaskWithInheritedMethod(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            one(action).run();
        }});
        task.execute();
    }

    @Test
    public void actionWorksForProtectedExecuteMethods() {
        final Runnable action = context.mock(Runnable.class);
        final TaskWithProtectedMethod task = new TaskWithProtectedMethod(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            one(action).run();
        }});
        task.execute();
    }

    public static class TestTask extends DefaultTask {
        final Runnable action;

        public TestTask(Runnable action) {
            super(HelperUtil.createRootProject(), "someName");
            this.action = action;
        }

        @TaskAction
        public void doStuff() {
            action.run();
        }
    }

    public static class TaskWithInheritedMethod extends TestTask {
        public TaskWithInheritedMethod(Runnable action) {
            super(action);
        }
    }

    public static class TaskWithProtectedMethod extends DefaultTask {
        private final Runnable action;

        public TaskWithProtectedMethod(Runnable action) {
            super(HelperUtil.createRootProject(), "someName");
            this.action = action;
        }

        @TaskAction
        protected void doStuff() {
            action.run();
        }
    }

    public static class TaskWithStaticMethod extends DefaultTask {
        @TaskAction
        public static void doStuff() {
        }
    }

    public static class TaskWithMultipleMethods extends TestTask {
        public TaskWithMultipleMethods(Runnable action) {
            super(action);
        }

        @TaskAction
        public void aMethod() {
            action.run();
        }

        @TaskAction
        public void anotherMethod() {
            action.run();
        }
    }

    public static class TaskWithParamMethod extends DefaultTask {
        @TaskAction
        public void doStuff(int value) {
        }
    }
}
