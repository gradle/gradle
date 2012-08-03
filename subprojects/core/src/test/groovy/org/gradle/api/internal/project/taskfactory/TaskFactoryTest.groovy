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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.TaskInstantiationException
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.HelperUtil
import org.junit.Test
import spock.lang.Specification

class TaskFactoryTest extends Specification {
    final ClassGenerator generator = Mock()
    final Instantiator instantiator = Mock()
    final ProjectInternal project = HelperUtil.createRootProject()
    final TaskFactory taskFactory = new TaskFactory(generator)

    def setup() {
        _ * generator.generate(_) >> { Class type -> type }
    }

    public void testCreateTask() {
        when:
        Task task = taskFactory.createTask(project, [name: "task"]);

        then:
        task instanceof DefaultTask
        task.project == project
        task.name == 'task'
        task.actions.isEmpty()
    }

    public void testCannotCreateTaskWithNoName() {
        when:
        taskFactory.createTask(project, [:])

        then:
        InvalidUserDataException e = thrown()
        e.message == "The task name must be provided."
    }

    public void testCreateTaskOfTypeWithNoArgsConstructor() {
        when:
        Task task = taskFactory.createTask(project, [name: 'task', type: TestDefaultTask.class])

        then:
        task instanceof TestDefaultTask
    }

    public void decoratesTaskType() {
        when:
        Task task = taskFactory.createTask(project, [name: 'task', type: TestDefaultTask.class])

        then:
        task instanceof DecoratedTask

        and:
        1 * generator.generate(TestDefaultTask) >> DecoratedTask
    }

    public void testCreateTaskWithDependencies() {
        when:
        Task task = taskFactory.createTask(project, [name: 'task', dependsOn: "/path1"])

        then:
        task.dependsOn == ["/path1"] as Set
    }

    public void testCreateTaskWithAction() {
        Action<Task> action = Mock()

        when:
        Task task = taskFactory.createTask(project, [name: 'task', action: action])

        then:
        task.actions.size() == 1
        task.actions[0].action == action
    }

    public void testCreateTaskWithActionClosure() {
        Closure cl = Mock()

        when:
        Task task = taskFactory.createTask(project, [name: 'task', action: cl])

        then:
        task.actions.size() == 1
        task.actions[0].closure == cl
    }

    public void testCreateTaskForTypeWithMissingConstructor() {
        when:
        taskFactory.createTask(project, [name: 'task', type:  MissingConstructorTask])

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create task of type 'MissingConstructorTask' as it does not have a public no-args constructor."
    }

    public void testCreateTaskForTypeWhichDoesNotImplementTask() {
        when:
        taskFactory.createTask(project, [name: 'task', type:  NotATask])

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create task of type 'NotATask' as it does not implement the Task interface."
    }

    public void testCreateTaskWhenConstructorThrowsException() {
        when:
        taskFactory.createTask(project, [name: 'task', type:  CannotConstructTask])

        then:
        TaskInstantiationException e = thrown()
        e.message == "Could not create task of type 'CannotConstructTask'."
        e.cause == CannotConstructTask.failure
    }

    public void createTaskWithDescription() {
        when:
        Task task = taskFactory.createTask(project, [name: 'task', description: "some task"])

        then:
        task.description == "some task"
    }

    public void createTaskWithGroup() {
        when:
        Task task = taskFactory.createTask(project, [name: 'task', group: "some group"])

        then:
        task.group == "some group"
    }

    public static class TestDefaultTask extends DefaultTask {
    }

    public static class DecoratedTask extends TestDefaultTask {
    }

    public static class MissingConstructorTask extends DefaultTask {
        public MissingConstructorTask(Integer something) {
        }
    }

    public static class NotATask {
    }

    public static class CannotConstructTask extends DefaultTask {
        final static failure = new RuntimeException("fail")

        public CannotConstructTask() {
            throw failure;
        }
    }

}
