/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.Task;
import org.junit.Before;
import org.junit.Test;

import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class DefaultTaskPlanExecutorTest extends AbstractTaskPlanExecutorTest {
    @Before
    public void setUp() {
        setUpBase();
        taskExecutionPlan = new DefaultTaskExecutionPlan();
        taskPlanExecutor = new DefaultTaskPlanExecutor(taskArtifactStateCacheAccess);
    }

    @Test
    public void testExecutesDependenciesInNameOrderWhenInMultipleProject() {
        Task a = helper.task(sub1, "a");
        Task b = helper.task(sub1, "b");
        Task c = helper.task(sub2, "c");
        Task d = helper.task(root, "d", b, a, c);

        addTasksToPlan(d);
        execute();

        assertThat(completedTasks, equalTo(toList(a, b, c, d)));
    }
}
