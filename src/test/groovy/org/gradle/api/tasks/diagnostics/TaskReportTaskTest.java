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

import org.gradle.api.Task;
import org.gradle.api.Rule;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

@RunWith(JMock.class)
public class TaskReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private TaskReportRenderer renderer;
    private ProjectInternal project;
    private TaskReportTask task;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        renderer = context.mock(TaskReportRenderer.class);
        project = context.mock(ProjectInternal.class);

        context.checking(new Expectations(){{
            allowing(project).absolutePath("list");
            will(returnValue(":path"));
        }});

        task = new TaskReportTask(project, "list");
        task.setRenderer(renderer);
    }

    @Test
    public void passesEachTaskToRenderer() throws IOException {
        context.checking(new Expectations() {{
            Task task1 = context.mock(Task.class, "task1");
            Task task2 = context.mock(Task.class, "task2");

            List<String> testDefaultTasks = WrapUtil.toList("defaultTask1", "defaultTask2");
            allowing(project).getDefaultTasks();
            will(returnValue(testDefaultTasks));

            one(project).getTasks();
            will(returnValue(GUtil.map("task2", task2, "task1", task1)));

            allowing(project).getRules();
            will(returnValue(WrapUtil.toList()));

            allowing(task2).compareTo(task1);
            will(returnValue(1));
            
            Sequence sequence = context.sequence("seq");

            one(renderer).addDefaultTasks(testDefaultTasks);
            inSequence(sequence);

            one(renderer).addTask(task1);
            inSequence(sequence);

            one(renderer).addTask(task2);
            inSequence(sequence);
        }});

        task.generate(project);
    }

    @Test
    public void passesEachRuleToRenderer() throws IOException {
        context.checking(new Expectations() {{
            Rule rule1 = context.mock(Rule.class, "rule1");
            Rule rule2 = context.mock(Rule.class, "rule2");

            List<String> defaultTasks = WrapUtil.toList();
            allowing(project).getDefaultTasks();
            will(returnValue(defaultTasks));

            one(project).getTasks();
            will(returnValue(GUtil.map()));

            one(project).getRules();
            will(returnValue(WrapUtil.toList(rule1, rule2)));

            Sequence sequence = context.sequence("seq");

            one(renderer).addDefaultTasks(defaultTasks);
            inSequence(sequence);

            one(renderer).addRule(rule1);
            inSequence(sequence);

            one(renderer).addRule(rule2);
            inSequence(sequence);
        }});

        task.generate(project);
    }
}
