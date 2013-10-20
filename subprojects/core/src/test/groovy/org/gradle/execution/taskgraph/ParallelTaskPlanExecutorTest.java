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

import java.util.concurrent.CountDownLatch;

import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ParallelTaskPlanExecutorTest extends AbstractTaskPlanExecutorTest {
    @Before
    public void setUp() {
        setUpBase();

        taskExecutionPlan = new DefaultTaskExecutionPlan();
        taskPlanExecutor = new ParallelTaskPlanExecutor(taskArtifactStateCacheAccess, 1);
    }

    @Test
    public void testExecutesDependenciesInNameOrderWhenInMultipleProject() {
        Task a = helper.task(sub1, "a");
        Task b = helper.task(sub1, "b");
        Task c = helper.task(sub2, "c");
        Task d = helper.task(sub2, "d");
        Task e = helper.task(root, "e", b, a, c, d);

        addTasksToPlan(e);
        execute();

        assertThat("wrong start order", startedTasks, equalTo(toList(a, b, c, d, e)));
        assertThat("wrong completion order", completedTasks, equalTo(toList(a, b, c, d, e)));
    }

    @Test
    public void testExecutesOnlyOneDependencyPerProjectSimultaneouslyWhenInMultipleProjects() throws InterruptedException {
        final CountDownLatch latchA = new CountDownLatch(1);
        final CountDownLatch latchE = new CountDownLatch(1);

        // 1: worker 1, first free item to pick by NameOrder
        final Task a = helper.blockingTask(sub1, "a", null, latchA, null);
        // 3: waiting for task a in same project to complete -- > release c
        final Task b = helper.task(sub1, "b");
        // 2: worker 2, first free item to pick by NameOrder in other project
        final Task c = helper.task(sub2, "c");
        // 4: waiting for task c in same project to complete --> release a
        final Task d = helper.task(sub2, "d", null, latchA);
        // 5: waiting for all dependencies to complete
        final Task e = helper.task(root, "e", null, latchE, b, a, c, d);

        taskPlanExecutor = new ParallelTaskPlanExecutor(taskArtifactStateCacheAccess, 2);

        addTasksToPlan(e);
        execute();

        latchE.await();

        assertThat("wrong start order", startedTasks, equalTo(toList(a, c, d, b, e)));
        assertThat("wrong completion order", completedTasks, equalTo(toList(c, d, a, b, e)));
    }

    @Test
    public void testExecutionPlanHonoursMutexes() throws InterruptedException {
        final CountDownLatch latchA = new CountDownLatch(1);
        final CountDownLatch latchD = new CountDownLatch(1);
        final CountDownLatch latchE = new CountDownLatch(1);
        String mutex = "mutex";

        // 1: worker 1, first free item to pick by NameOrder, setting mutex
        final Task a = helper.blockingTask(sub1, "a", mutex, latchA, null);
        // 3: waiting for task a in same project to complete -- > release d
        final Task b = helper.task(sub1, "b", null, latchD);
        // 4: worker 2, skipped at first as blocked by mutex, later waiting for task d in same project to complete
        final Task c = helper.task(sub2, "c", mutex);
        // 2: worker 2, first free item to pick by NameOrder in other project, no blocking mutex --> -- > release a
        final Task d = helper.blockingTask(sub2, "d", null, latchD, latchA);
        // 5: waiting for all dependencies to complete
        final Task e = helper.task(root, "e", null, latchE, b, a, c, d);

        taskPlanExecutor = new ParallelTaskPlanExecutor(taskArtifactStateCacheAccess, 2);

        addTasksToPlan(e);
        execute();

        latchE.await();

        assertThat("wrong start order", startedTasks, equalTo(toList(a, d, b, c, e)));
        assertThat("wrong completion order", completedTasks, equalTo(toList(a, b, d, c, e)));
    }
}
