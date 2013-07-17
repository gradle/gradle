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
import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.util.HelperUtil
import spock.lang.Specification

class TaskFactoryTest extends Specification {
    final ClassGenerator generator = Mock()
    final Instantiator instantiator = Mock()
    final ProjectInternal project = HelperUtil.createRootProject()
    final ITaskFactory taskFactory = new TaskFactory(generator).createChild(project, instantiator)

    def setup() {
        _ * generator.generate(_) >> { Class type -> type }
        _ * instantiator.newInstance(_) >> { args -> args[0].newInstance() }
    }

    public void testUsesADefaultTaskTypeWhenNoneSpecified() {
        when:
        Task task = taskFactory.createTask([name: "task"]);

        then:
        task instanceof DefaultTask
    }

    public void injectsProjectAndNameIntoTask() {
        when:
        Task task = taskFactory.createTask([name: "task"]);

        then:
        task.project == project
        task.name == 'task'
    }

    public void testCannotCreateTaskWithNoName() {
        when:
        taskFactory.createTask([:])

        then:
        InvalidUserDataException e = thrown()
        e.message == "The task name must be provided."
    }

    public void testCreateTaskOfTypeWithNoArgsConstructor() {
        when:
        Task task = taskFactory.createTask([name: 'task', type: TestDefaultTask.class])

        then:
        task instanceof TestDefaultTask
    }

    public void instantiatesAnInstanceOfTheDecoratedTaskType() {
        when:
        Task task = taskFactory.createTask([name: 'task', type: TestDefaultTask.class])

        then:
        task instanceof DecoratedTask

        and:
        1 * generator.generate(TestDefaultTask) >> DecoratedTask
        1 * instantiator.newInstance(DecoratedTask) >> { new DecoratedTask() }
        0 * _._
    }

    public void testCreateTaskWithDependencies() {
        when:
        Task task = taskFactory.createTask([name: 'task', dependsOn: "/path1"])

        then:
        task.dependsOn == ["/path1"] as Set
    }

    public void taskCreationFailsWithUnknownArguments() {
        when:
        taskFactory.createTask([name: 'task', dependson: 'anotherTask'])

        then:
        InvalidUserDataException exception = thrown()
        exception.message == "Could not create task 'task': Unknown argument(s) in task definition: [dependson]"

        when:
        taskFactory.createTask([name: 'task', Type: NotATask])

        then:
        exception = thrown()
        exception.message == "Could not create task 'task': Unknown argument(s) in task definition: [Type]"

    }

    public void testCreateTaskWithAction() {
        Action<Task> action = Mock()

        when:
        Task task = taskFactory.createTask([name: 'task', action: action])

        then:
        task.actions.size() == 1
        task.actions[0].action == action
    }

    public void testCreateTaskWithActionClosure() {
        Closure cl = Mock()

        when:
        Task task = taskFactory.createTask([name: 'task', action: cl])

        then:
        task.actions.size() == 1
        task.actions[0].closure == cl
    }

    public void testCreateTaskForTypeWhichDoesNotImplementTask() {
        when:
        taskFactory.createTask([name: 'task', type: NotATask])

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create task of type 'NotATask' as it does not implement the Task interface."
    }

    public void wrapsFailureToCreateTaskInstance() {
        def failure = new RuntimeException()

        when:
        taskFactory.createTask([name: 'task', type: TestDefaultTask])

        then:
        TaskInstantiationException e = thrown()
        e.message == "Could not create task of type 'TestDefaultTask'."
        e.cause == failure

        and:
        _ * instantiator.newInstance(TestDefaultTask) >> { throw new ObjectInstantiationException(TestDefaultTask, failure) }
    }

    public void createTaskWithDescription() {
        when:
        Task task = taskFactory.createTask([name: 'task', description: "some task"])

        then:
        task.description == "some task"
    }

    public void createTaskWithGroup() {
        when:
        Task task = taskFactory.createTask([name: 'task', group: "some group"])

        then:
        task.group == "some group"
    }

    public static class TestDefaultTask extends DefaultTask {
    }

    public static class DecoratedTask extends TestDefaultTask {
    }

    public static class NotATask {
    }
}
