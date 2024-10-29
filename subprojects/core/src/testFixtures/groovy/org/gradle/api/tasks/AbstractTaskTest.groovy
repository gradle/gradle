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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction
import org.gradle.api.model.ObjectFactory
import org.gradle.api.specs.Spec
import org.gradle.internal.Actions
import org.gradle.internal.MutableBoolean
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.junit.Assert.assertTrue

abstract class AbstractTaskTest extends AbstractProjectBuilderSpec {
    public static final String TEST_TASK_NAME = "testTask"

    protected DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry()
    protected ObjectFactory objectFactory = TestUtil.objectFactory()

    abstract DefaultTask getTask()

    def <T extends DefaultTask> T createTask(Class<T> type) {
        return createTask(type, project, TEST_TASK_NAME)
    }

    Task createTask(ProjectInternal project, String name) {
        return createTask(getTask().getClass(), project, name)
    }

    def <T extends DefaultTask> T createTask(Class<T> type, ProjectInternal project, String name) {
        Task task = project.getServices().get(TaskInstantiator.class).create(name, type)
        assertTrue(type.isAssignableFrom(task.getClass()))
        return type.cast(task)
    }

    def setup() {
        serviceRegistry.add(ObjectFactory.class, objectFactory)
    }

    def "test Task"() {
        expect:
        getTask().isEnabled()
        TEST_TASK_NAME.equals(getTask().getName())
        getTask().getDescription() == null
        project.is(getTask().getProject())
        getTask().getStandardOutputCapture() != null
        getTask().getInputs() != null
        getTask().getOutputs() != null
        getTask().getOnlyIf() != null
        getTask().getOnlyIf().isSatisfiedBy(getTask())
    }

    def "test Path"() {
        given:
        def childProject = TestUtil.createChildProject(project, "child")
        childProject.getProjectDir().mkdirs()
        def childchildProject = TestUtil.createChildProject(childProject, "childchild")
        childchildProject.getProjectDir().mkdirs()

        and:
        def task = createTask(project, TEST_TASK_NAME)
        def childTask = createTask(childProject, TEST_TASK_NAME)
        def childChildTask = createTask(childchildProject, TEST_TASK_NAME)

        expect:
        task.getPath() == Project.PATH_SEPARATOR + TEST_TASK_NAME
        childTask.getPath() == Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + TEST_TASK_NAME
        childChildTask.getPath() == Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + "childchild" + Project.PATH_SEPARATOR + TEST_TASK_NAME
    }

    def "test toString"() {
        expect:
        "task '" + getTask().getPath() + "'" == getTask().toString()
    }

    def "test setActions"() {
        given:
        getTask().setActions([])

        when:
        getTask().getActions().add(Actions.doNothing())
        getTask().getActions().add(Actions.doNothing())

        then:
        getTask().getActions().size() == 2

        when:
        List<Action<? super Task>> actions = new ArrayList<>()
        actions.add(Actions.doNothing())
        getTask().setActions(actions)

        then:
        getTask().getActions().size() == 1
    }

    def "can replace an action"() {
        given:
        getTask().setActions([])

        when:
        getTask().getActions().add(Actions.doNothing())
        getTask().getActions().set(0, { task -> throw new RuntimeException() } as Action)

        then:
        getTask().getActions().size() == 1
        getTask().getActions()[0] instanceof InputChangesAwareTaskAction

        when:
        getTask().getActions()[0].execute(getTask())

        then:
        thrown(RuntimeException)
    }

    def "addAction with null throws"() {
        when:
        getTask().doLast((Closure) null)

        then:
        InvalidUserDataException e = thrown()
        e.message.equals("Action must not be null!")
    }

    def "test getDescription"() {
        given:
        def testDescription = "testDescription"
        getTask().setDescription(testDescription)

        expect:
        testDescription == getTask().getDescription()
    }

    def "can specify onlyIf predicate using closure"() {
        given:
        def task = getTask()

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.onlyIf({ false })

        then:
        !task.getOnlyIf().isSatisfiedBy(task)
    }

    def "can specify onlyIf predicate using spec"() {
        given:
        final task = getTask()
        final Spec<Task> spec = Mock(Spec.class)
        spec.isSatisfiedBy(task) >> false

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.onlyIf(spec)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)
    }

    def "can specify onlyIf predicate using description and spec"() {
        given:
        final task = getTask()
        final Spec<Task> spec = Mock(Spec.class)
        spec.isSatisfiedBy(task) >> false

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.onlyIf("Always false", spec)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)
        def foundSpec = task.getOnlyIf().findUnsatisfiedSpec(task)
        foundSpec != null
        foundSpec.displayName == "Always false"
    }

    def "onlyIf predicate is true when task is enabled and all predicates are true"() {
        given:
        final MutableBoolean condition1 = new MutableBoolean(true)
        final MutableBoolean condition2 = new MutableBoolean(true)

        def task = getTask()
        task.onlyIf(new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return condition1.get()
            }
        })

        task.onlyIf("Condition 2 was not met", new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return condition2.get()
            }
        })

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.setEnabled(false)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)
        def disabledSpec = task.getOnlyIf().findUnsatisfiedSpec(task)
        disabledSpec != null
        disabledSpec.displayName == "Task is enabled"

        when:
        task.setEnabled(true)
        condition1.set(false)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)
        def condition1Spec = task.getOnlyIf().findUnsatisfiedSpec(task)
        condition1Spec != null
        condition1Spec.displayName == "Task satisfies onlyIf spec"

        when:
        condition1.set(true)
        condition2.set(false)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)
        def condition2Spec = task.getOnlyIf().findUnsatisfiedSpec(task)
        condition2Spec != null
        condition2Spec.displayName == "Condition 2 was not met"

        when:
        condition2.set(true)

        then:
        task.getOnlyIf().isSatisfiedBy(task)
    }

    def "can replace onlyIf spec"() {
        given:
        final MutableBoolean condition1 = new MutableBoolean(true)
        final task = getTask()
        final Spec spec = Mock(Spec.class)
        task.onlyIf(spec)
        task.setOnlyIf(new Spec<Task>() {
            boolean isSatisfiedBy(Task element) {
                return condition1.get()
            }
        })

        expect:
        task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.setEnabled(false)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)

        when:
        task.setEnabled(true)
        condition1.set(false)

        then:
        !task.getOnlyIf().isSatisfiedBy(task)

        when:
        condition1.set(true)

        then:
        task.getOnlyIf().isSatisfiedBy(task)
    }
}
