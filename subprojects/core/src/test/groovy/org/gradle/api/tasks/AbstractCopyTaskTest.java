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
package org.gradle.api.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.file.copy.CopyActionImpl;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class AbstractCopyTaskTest extends AbstractTaskTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private TestCopyTask task;

    @Before
    public void setUp() {
        task = createTask(TestCopyTask.class);
        task.action = context.mock(CopyActionImpl.class);
        task.defaultSource = context.mock(FileCollection.class);
    }

    @Override
    public AbstractTask getTask() {
        return task;
    }

    @Test
    public void usesDefaultSourceWhenNoSourceHasBeenSpecified() {
        context.checking(new Expectations() {{
            one(task.action).hasSource();
            will(returnValue(false));

        }});
        assertThat(task.getSource(), sameInstance(task.defaultSource));
    }

    @Test
    public void doesNotUseDefaultSourceWhenSourceHasBeenSpecifiedOnSpec() {
        final FileTree source = context.mock(FileTree.class, "source");
        context.checking(new Expectations() {{
            one(task.action).hasSource();
            will(returnValue(true));
            one(task.action).getAllSource();
            will(returnValue(source));
        }});
        assertThat(task.getSource(), sameInstance((FileCollection) source));
    }


    @Test
    public void copySpecMethodsDelegateToMainSpecOfCopyAction() {
        context.checking(new Expectations() {{
            one(task.action).include("include");
            one(task.action).from("source");
        }});

        assertThat(task.include("include"), sameInstance((AbstractCopyTask) task));
        assertThat(task.from("source"), sameInstance((AbstractCopyTask) task));
    }

    public static class TestCopyTask extends AbstractCopyTask {
        CopyActionImpl action;
        FileCollection defaultSource;

        @Override
        protected CopyActionImpl getCopyAction() {
            return action;
        }

        @Override
        @SuppressWarnings("deprecation")
        public FileCollection getDefaultSource() {
            return defaultSource;
        }

        @Override
        protected void postCopyCleanup() {

        }
    }
}
