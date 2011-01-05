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
package org.gradle.api.internal.tasks

import java.util.concurrent.Callable
import org.gradle.api.Buildable
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.WrapUtil.toList
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.GradleException

@RunWith (JMock.class)
public class DefaultTaskDependencyTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final TaskResolver resolver = context.mock(TaskResolver.class)
    private final DefaultTaskDependency dependency = new DefaultTaskDependency(resolver);
    private Task task;
    private Task otherTask;

    @Before
    public void setUp() throws Exception {
        task = context.mock(Task.class, "task");
        otherTask = context.mock(Task.class, "otherTask");
    }

    @Test
    public void hasNoDependenciesByDefault() {
        assertThat(dependency.getDependencies(task), equalTo(WrapUtil.toSet()));
    }

    @Test
    public void canDependOnATaskInstance() {
        dependency.add(otherTask);

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void canDependOnATaskDependency() {
        final TaskDependency otherDependency = context.mock(TaskDependency.class);
        dependency.add(otherDependency);

        context.checking({
            one(otherDependency).getDependencies(task);
            will(returnValue(toSet(otherTask)));
        });

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void canDependOnAClosure() {
        dependency.add({Task suppliedTask ->
            assertThat(suppliedTask, sameInstance(task))
            otherTask
        })

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void closureCanReturnNull() {
        dependency.add({ null })

        assertThat(dependency.getDependencies(task), isEmpty());
    }

    @Test
    public void canDependOnABuildable() {
        Buildable buildable = context.mock(Buildable)
        TaskDependency otherDependency = context.mock(TaskDependency)

        dependency.add(buildable)

        context.checking {
            one(buildable).getBuildDependencies()
            will(returnValue(otherDependency))
            one(otherDependency).getDependencies(task)
            will(returnValue(toSet(otherTask)))
        }

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void canDependOnAnIterable() {
        List tasks = [otherTask]
        Iterable iterable = { tasks.iterator() } as Iterable

        dependency.add(iterable)

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @org.junit.Test
    public void canDependOnACallable() {
        Callable callable = context.mock(Callable)

        dependency.add(callable)

        context.checking {
            one(callable).call()
            will(returnValue(otherTask))
        }
        
        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @org.junit.Test
    public void callableCanReturnNull() {
        Callable callable = context.mock(Callable)

        dependency.add(callable)

        context.checking {
            one(callable).call()
            will(returnValue(null))
        }

        assertThat(dependency.getDependencies(task), isEmpty());
    }

    @Test
    public void failsForOtherObjectsWhenNoResolverProvided() {
        StringBuffer dep = new StringBuffer("task")

        DefaultTaskDependency dependency = new DefaultTaskDependency()
        dependency.add(dep)

        try {
            dependency.getDependencies(task)
            fail()
        } catch (GradleException e) {
            assertThat(e.cause, instanceOf(IllegalArgumentException))
            assertThat(e.cause.message, equalTo("Cannot convert $dep to a task." as String))
        }
    }
    
    @Test
    public void resolvesOtherObjects() {

        dependency.add(9);

        context.checking({
            one(resolver).resolveTask(9);
            will(returnValue(otherTask));
        });

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void flattensCollections() {
        dependency.add(toList(otherTask));

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void flattensMaps() {
        dependency.add([key: otherTask])

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void flattensArrays() {
        dependency.add([[otherTask] as Task[]])

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void canNestIterablesAndMapsAndClosuresAndCallables() {
        Map nestedMap = [task: otherTask]
        Iterable nestedCollection = [nestedMap]
        Callable nestedCallable = {nestedCollection} as Callable
        Closure nestedClosure = {nestedCallable}
        List collection = [nestedClosure]
        Closure closure = {collection}
        Object[] array = [closure] as Object[]
        Map map = [key: array]
        Callable callable = {map} as Callable
        dependency.add(callable)

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }
}
