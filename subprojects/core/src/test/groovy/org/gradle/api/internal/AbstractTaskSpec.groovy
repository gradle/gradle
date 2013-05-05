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
import org.gradle.util.HelperUtil
import org.junit.Test
import spock.lang.Specification

import static org.junit.Assert.assertTrue

class AbstractTaskSpec extends Specification {

    private DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry();
    private Instantiator instantiator = new DependencyInjectingInstantiator(serviceRegistry);
    private final AnnotationProcessingTaskFactory rootFactory = new AnnotationProcessingTaskFactory(new TaskFactory(new AsmBackedClassGenerator()));

    public static class TestTask extends AbstractTask {

    }

    public Task createTask(String name) {
        AbstractProject project = HelperUtil.createRootProject();
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        registry.add(Instantiator.class, new DirectInstantiator());
        Task task = rootFactory.createChild(project, instantiator).createTask(GUtil.map(Task.TASK_TYPE, TestTask.class, Task.TASK_NAME, name));
        assertTrue(TestTask.class.isAssignableFrom(task.getClass()));
        return TestTask.class.cast(task);
    }

    @Test
    def "can add action to a task via Task.getActions() List"() {
        setup:
        TestTask task = createTask("task")
        when:
        def actions = task.getActions()
        and:
        def action = Mock(Action)

        actions.add(action)
        then:
        task.actions.size() == 1
        actions.size() == 1
    }
}
