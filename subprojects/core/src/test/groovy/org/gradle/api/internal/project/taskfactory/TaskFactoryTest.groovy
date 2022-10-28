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

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.api.tasks.TaskInstantiationException
import org.gradle.internal.instantiation.DeserializationInstantiator
import org.gradle.internal.instantiation.InstanceGenerator
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.Path
import org.gradle.util.TestUtil

class TaskFactoryTest extends AbstractProjectBuilderSpec {
    def instantiationScheme = Mock(InstantiationScheme)
    def instantiator = Mock(InstanceGenerator)
    def deserializeInstantiator = Mock(DeserializationInstantiator)
    ITaskFactory taskFactory

    def setup() {
        taskFactory = new TaskFactory().createChild(project, instantiationScheme)
        _ * instantiationScheme.instantiator() >> instantiator
        _ * instantiationScheme.deserializationInstantiator() >> deserializeInstantiator
        _ * instantiator.newInstanceWithDisplayName(_, _, _) >> { args -> TestUtil.newInstance(args[0]) }
    }

    void injectsProjectAndNameIntoTask() {
        when:
        Task task = taskFactory.create(new TaskIdentity(DefaultTask, 'task', null, Path.path(':task'), null, 12))

        then:
        task.project == project
        task.name == 'task'
    }

    void testCreateTaskOfTypeWithNoArgsConstructor() {
        when:
        Task task = taskFactory.create(new TaskIdentity(TestDefaultTask, 'task', null, Path.path(':task'), null, 12))

        then:
        task instanceof TestDefaultTask
    }

    void testCreateTaskWhereSuperTypeOfDefaultImplementationRequested() {
        when:
        Task task = taskFactory.create(new TaskIdentity(type, 'task', null, Path.path(':task'), null, 12))

        then:
        task instanceof DefaultTask

        where:
        type << [Task, DefaultTask]
    }

    void testCreateTaskForDeserialization() {
        when:
        Task task = taskFactory.create(new TaskIdentity(TestDefaultTask, 'task', null, Path.path(':task'), null, 12), (Object[]) null)

        then:
        1 * deserializeInstantiator.newInstance(TestDefaultTask, AbstractTask) >> { new TestDefaultTask() }
        task instanceof TestDefaultTask
    }

    void testCreateTaskForTypeWhichDoesNotImplementTask() {
        when:
        taskFactory.create(new TaskIdentity(NotATask, 'task', null, Path.path(':task'), null, 12))

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create task ':task' of type 'NotATask' as it does not implement the Task interface."
    }

    void testCreateTaskForUnsupportedType() {
        when:
        taskFactory.create(new TaskIdentity(taskType, 'task', null, Path.path(':task'), null, 12))

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create task ':task' of type '$taskType.simpleName' as this type is not supported for task registration."

        where:
        taskType << [AbstractTask, TaskInternal]
    }

    void testCreateTaskForTypeDirectlyExtendingAbstractTask() {
        when:
        taskFactory.create(new TaskIdentity(ExtendsAbstractTask, 'task', null, Path.path(':task'), null, 12))

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create task ':task' of type 'ExtendsAbstractTask' as directly extending AbstractTask is not supported."
    }

    void wrapsFailureToCreateTaskInstance() {
        def failure = new RuntimeException()

        when:
        taskFactory.create(new TaskIdentity(TestDefaultTask, 'task', null, Path.path(':task'), null, 12))

        then:
        TaskInstantiationException e = thrown()
        e.message == "Could not create task of type 'TestDefaultTask'."
        e.cause == failure

        and:
        _ * instantiator.newInstanceWithDisplayName(TestDefaultTask, _, _) >> { throw new ObjectInstantiationException(TestDefaultTask, failure) }
    }

    static class TestDefaultTask extends DefaultTask {
    }

    static class DecoratedTask extends TestDefaultTask {
    }

    static class NotATask {
    }

    static class ExtendsAbstractTask extends AbstractTask {
    }
}
