/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory
import org.gradle.api.internal.project.taskfactory.DefaultTaskClassInfoStore
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.api.internal.project.taskfactory.TaskIdentityFactory
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.specs.Spec
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.id.ConfigurationCacheableIdFactory
import org.gradle.internal.Actions
import org.gradle.internal.MutableBoolean
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.junit.Assert.assertFalse

abstract class AbstractSpockTaskTest extends AbstractProjectBuilderSpec {
    public static final String TEST_TASK_NAME = "taskname"

    def taskClassInfoStore = new DefaultTaskClassInfoStore(new TestCrossBuildInMemoryCacheFactory())
    def taskIdentityFactory = new TaskIdentityFactory(new ConfigurationCacheableIdFactory())
    def taskFactory = new AnnotationProcessingTaskFactory(DirectInstantiator.INSTANCE, taskClassInfoStore, new TaskFactory())

    abstract DefaultTask getTask()

    def <T extends DefaultTask> T createTask(Class<T> type) {
        return createTask(type, project, TEST_TASK_NAME)
    }

    Task createTask(ProjectInternal project, String name) {
        return createTask(getTask().getClass(), project, name)
    }

    def <T extends DefaultTask> T createTask(Class<T> type, ProjectInternal project, String name) {
        Task task = new TaskInstantiator(taskIdentityFactory, taskFactory.createChild(project, TestUtil.instantiatorFactory().decoratingLenientScheme), project).create(name, type)
        assert type.isAssignableFrom(task.getClass())
        return type.cast(task)
    }

    def testTask() {
        expect:
        getTask().isEnabled()
        TEST_TASK_NAME ==  getTask().getName()
        getTask().getDescription() == null
        project.is( getTask().getProject())
        getTask().getStandardOutputCapture() != null
        getTask().getInputs() != null
        getTask().getOutputs() != null
        getTask().getOnlyIf() != null
        getTask().getOnlyIf().isSatisfiedBy(getTask())
    }

    def testPath() {
        ProjectInternal childProject = TestUtil.createChildProject(project, "child")
        childProject.getProjectDir().mkdirs()
        ProjectInternal childchildProject = TestUtil.createChildProject(childProject, "childchild")
        childchildProject.getProjectDir().mkdirs()

        when:
        Task task = createTask(project, TEST_TASK_NAME)

        then:
        Project.PATH_SEPARATOR + TEST_TASK_NAME ==  task.getPath()

        when:
        task = createTask(childProject, TEST_TASK_NAME)

        then:
        Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + TEST_TASK_NAME ==  task.getPath()

        when:
        task = createTask(childchildProject, TEST_TASK_NAME)

        then:
        Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + "childchild" + Project.PATH_SEPARATOR + TEST_TASK_NAME ==  task.getPath()
    }

    def testDependsOn() {
        Task dependsOnTask = createTask(project, "somename")
        Task task = createTask(project, TEST_TASK_NAME)
        project.getTasks().create("path1")
        project.getTasks().create("path2")

        when:
        task.dependsOn(Project.PATH_SEPARATOR + "path1")

        then:
        TaskDependencyMatchers.dependsOn("path1").matches(task)

        when:
        task.dependsOn("path2", dependsOnTask)

        then:
        TaskDependencyMatchers.dependsOn("path1", "path2", "somename").matches(task)
    }

    def testToString() {
        "task '" + getTask().getPath() + "'" ==  getTask().toString()
    }

    def testSetActions() {
        when:
        Action action1 = Actions.doNothing()
        getTask().actions = [action1]

        Action action2 = Actions.doNothing()
        getTask().actions = [action2]

        then:
        [new AbstractTask.TaskActionWrapper(action2, "doLast(Action)")] ==  getTask().actions
    }

    def testAddActionWithNull() {
        when:
        getTask().doLast((Closure) null)

        then:
        thrown(InvalidUserDataException)
    }

    def setGetDescription() {
        when:
        String testDescription = "testDescription"
        getTask().setDescription(testDescription)

        then:
        testDescription ==  getTask().getDescription()
    }

    def canSpecifyOnlyIfPredicateUsingClosure() {
        DefaultTask task = getTask()

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.onlyIf(TestUtil.toClosure("{ task -> false }"))

        then:
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))
    }

    def canSpecifyOnlyIfPredicateUsingSpec() {
        final Spec<Task> spec = Mock()
        final DefaultTask task = getTask()

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.onlyIf(spec)

        then:
        spec.isSatisfiedBy(task) >> false
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))
    }

    def onlyIfPredicateIsTrueWhenTaskIsEnabledAndAllPredicatesAreTrue() {
        final MutableBoolean condition1 = new MutableBoolean(true)
        final MutableBoolean condition2 = new MutableBoolean(true)

        DefaultTask task = getTask()
        task.onlyIf {
            condition1.get()
        }
        task.onlyIf {
            condition2.get()
        }

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.setEnabled(false)

        then:
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))

        when:
        task.setEnabled(true)
        condition1.set(false)

        then:
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))

        when:
        condition1.set(true)
        condition2.set(false)

        then:
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))

        when:
        condition2.set(true)

        then:
        task.getOnlyIf().isSatisfiedBy(task)
    }

    def canReplaceOnlyIfSpec() {
        final MutableBoolean condition1 = new MutableBoolean(true)
        DefaultTask task = getTask()
        task.onlyIf(Mock(Spec))
        task.setOnlyIf {
            return condition1.get()
        }

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.setEnabled(false)

        then:
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))

        when:
        task.setEnabled(true)
        condition1.set(false)

        then:
        assertFalse(task.getOnlyIf().isSatisfiedBy(task))

        when:
        condition1.set(true)

        then:
        task.getOnlyIf().isSatisfiedBy(task)
    }
}
