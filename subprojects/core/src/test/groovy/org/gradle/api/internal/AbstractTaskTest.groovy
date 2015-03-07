/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.project.AbstractProject
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.GUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.junit.Assert.assertTrue

class AbstractTaskTest extends Specification {

    private DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry()
    private Instantiator instantiator = new DependencyInjectingInstantiator(serviceRegistry)
    private final AnnotationProcessingTaskFactory rootFactory = new AnnotationProcessingTaskFactory(new TaskFactory(new AsmBackedClassGenerator()))

    public static class TestTask extends AbstractTask {
    }

    public TaskInternal createTask(String name) {
        AbstractProject project = TestUtil.createRootProject()
        DefaultServiceRegistry registry = new DefaultServiceRegistry()
        registry.add(Instantiator, DirectInstantiator.INSTANCE)
        TaskInternal task = rootFactory.createChild(project, instantiator).createTask(GUtil.map(Task.TASK_TYPE, TestTask, Task.TASK_NAME, name))
        assertTrue(TestTask.isAssignableFrom(task.getClass()))
        return task
    }

    def "can add action to a task via Task.getActions() List"() {
        setup:
        TestTask task = createTask("task")
        when:
        def actions = task.actions
        and:
        def action = Mock(Action)

        actions.add(action)
        then:
        task.actions.size() == 1
        actions.size() == 1
    }

    def "can detect tasks with custom actions added"() {
        when:
        def task = createTask("task")

        then:
        !task.hasCustomActions

        when:
        task.prependParallelSafeAction {}

        then:
        !task.hasCustomActions

        when:
        task.doFirst {}

        then:
        task.hasCustomActions
    }
}
