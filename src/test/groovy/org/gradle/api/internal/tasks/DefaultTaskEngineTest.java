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
package org.gradle.api.internal.tasks;

import groovy.lang.MissingPropertyException;
import org.gradle.api.Rule;
import org.gradle.api.Task;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultTaskEngineTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private DefaultTaskEngine taskEngine = new DefaultTaskEngine();
    private static final String TASK_NAME = "taskName";
    private Task dynamicTask = context.mock(Task.class, "dynamic");
    private Task staticTask = context.mock(Task.class, "static");

    @Test
    public void testAccessTaskWithNoStaticAndNoDynamicTask() {
        assertThat(taskEngine.findTask("nonexisiting"), equalTo(null));
        assertThat(taskEngine.hasProperty("nonexisiting"), equalTo(false));
        assertThat(taskEngine.hasMethod("nonexisiting", HelperUtil.TEST_CLOSURE), equalTo(false));

    }

    @Test(expected = MissingPropertyException.class)
    public void testGetPropertyWithNoStaticAndNoDynamicTask() {
        taskEngine.getProperty("nonexisiting");
    }

    @Test
    public void testAccessTaskWithStaticTask() {
        addStaticTestTask(TASK_NAME);
        assertThat(taskEngine.findTask(TASK_NAME), sameInstance(staticTask));
        assertThat(taskEngine.getProperty(TASK_NAME), sameInstance(staticTask));
        assertThat(taskEngine.hasProperty(TASK_NAME), equalTo(true));
        assertThat(taskEngine.hasMethod(TASK_NAME, HelperUtil.TEST_CLOSURE), equalTo(true));
    }

    @Test
    public void testAccessTaskWithDynamicTask() {
        createRuleForTestTask();
        assertThat(taskEngine.findTask(TASK_NAME), sameInstance(dynamicTask));
        assertThat(taskEngine.getProperty(TASK_NAME), sameInstance(dynamicTask));
        assertThat(taskEngine.hasProperty(TASK_NAME), equalTo(true));
        assertThat(taskEngine.hasMethod(TASK_NAME, HelperUtil.TEST_CLOSURE), equalTo(true));
    }

    @Test
    public void testTaskWithStaticTaskAndDynamicTaskWithSameName() {
        addStaticTestTask(TASK_NAME);
        createRuleForTestTask();
        assertThat(taskEngine.findTask(TASK_NAME), sameInstance(staticTask));
        assertThat(taskEngine.getProperty(TASK_NAME), sameInstance(staticTask));
        assertThat(taskEngine.hasProperty(TASK_NAME), equalTo(true));
        assertThat(taskEngine.hasMethod(TASK_NAME, HelperUtil.TEST_CLOSURE), equalTo(true));
    }

    private Rule createRuleForTestTask() {
        Rule rule = new Rule() {
            public String getDescription() {
                return "";
            }

            public void apply(String taskName) {
                if (taskName.equals(TASK_NAME)) {
                    taskEngine.getTasks().add(TASK_NAME, dynamicTask);
                }
            }
        };
        taskEngine.addRule(rule);
        return rule;
    }

    private void addStaticTestTask(String taskName) {
        taskEngine.getTasks().add(taskName, staticTask);
    }

    @Test
    public void testGetRules() {
        Rule rule = createRuleForTestTask();
        assertThat(taskEngine.getRules(), equalTo(WrapUtil.toList(rule)));
    }

    @Test
    public void testGetProperties() {
        addStaticTestTask(TASK_NAME);
        assertThat(taskEngine.getProperties(), equalTo(WrapUtil.toMap(TASK_NAME, staticTask)));
    }

    @Test
    public void testInvokeMethodWithStaticTask() {
        addStaticTestTask(TASK_NAME);
        checkInvokeMethod(staticTask);
    }

    @Test
    public void testInvokeMethodWithDynamicTask() {
        createRuleForTestTask();
        checkInvokeMethod(dynamicTask);
    }

    @Test
    public void testInvokeMethodWithStaticTaskAndDynamicTaskWithSameName() {
        addStaticTestTask(TASK_NAME);
        createRuleForTestTask();
        checkInvokeMethod(staticTask);
    }


    private void checkInvokeMethod(final Task task) {
        final String description = "testDesc";
        context.checking(new Expectations() {{
            one(task).setDescription(description);
        }});
        taskEngine.invokeMethod(TASK_NAME, HelperUtil.toClosure("{description = '" + description + "' }"));
    }

    @Test(expected = MissingPropertyException.class)
    public void testSetProperty() {
        taskEngine.setProperty("name", dynamicTask);
    }

    @Test
    public void testAddTask() {
        context.checking(new Expectations() {{
            allowing(staticTask).getName();
            will(returnValue(TASK_NAME));
        }});
        taskEngine.addTask(staticTask);
        assertThat(taskEngine.getTasks().get(TASK_NAME), sameInstance(staticTask));
    }
}
