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

package org.gradle.api.internal.tasks;


import org.gradle.api.InvalidUserDataException
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.tasks.TaskDependency
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.GUtil
import org.gradle.util.HelperUtil
import spock.lang.Specification

import static java.util.Collections.singletonMap

public class DefaultTaskContainerTest extends Specification {

    private taskFactory = Mock(ITaskFactory)
    private project = Mock(ProjectInternal, name: "<project>")
    private taskCount = 1;
    private container = new DefaultTaskContainer(project, Mock(Instantiator), taskFactory, Mock(ProjectAccessListener))

    void "adds by Map"() {
        def options = singletonMap("option", "value")
        def task = task("task")
        taskFactory.createTask(options) >> task

        when:
        def added = container.add(options)

        then:
        added == task
        container.getByName("task") == task
    }

    void "adds by name"() {
        given:
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")
        taskFactory.createTask(options) >> task

        expect:
        container.add("task") == task
    }

    void "adds by name and type"() {
        given:
        def options = GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, Task.class)
        def task = task("task")
        taskFactory.createTask(options) >> task

        expect:
        container.add("task", Task.class) == task
    }

    void "adds by name and closure"() {
        given:
        final Closure action = HelperUtil.toClosure("{ description = 'description' }")
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")

        taskFactory.createTask(options) >> task

        when:
        def added = container.add("task", action)

        then:
        added == task
        1 * task.configure(action) >> task
    }

    void "replaces task by name"() {
        given:
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")
        taskFactory.createTask(options) >> task

        when:
        def replaced = container.replace("task")

        then:
        replaced == task
        container.getByName("task") == task
    }

    void "replaces by name and type"() {
        given:
        def options = GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, Task.class)
        def task = task("task")
        taskFactory.createTask(options) >> task

        expect:
        container.replace("task", Task.class) == task
    }

    void "does not fire rule when adding task"() {
        def rule = Mock(Rule)
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")

        container.addRule(rule)
        taskFactory.createTask(options) >> task

        when:
        container.add("task")

        then:
        0 * rule._
    }

    void "prevents duplicate tasks"() {
        given:
        def task = addTask("task")
        taskFactory.createTask(singletonMap(Task.TASK_NAME, "task")) >> { this.task("task") }

        when:
        container.add("task")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot add Mock for type 'TaskInternal' named '[task2]' as a task with that name already exists."
        container.getByName("task") == task
    }

    void "replaces duplicate task"() {
        given:
        addTask("task")
        def newTask = task("task")
        taskFactory.createTask(singletonMap(Task.TASK_NAME, "task")) >> newTask

        when:
        container.replace("task")

        then:
        container.getByName("task") == newTask
    }

    void "fails if unknown task is requested"() {
        when:
        container.getByName("unknown")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'unknown' not found in Mock for type 'ProjectInternal' named '<project>'."
    }

    void "finds tasks"() {
        when:
        def task = addTask("task")

        then:
        container.findByPath("unknown") == null
        container.findByPath("task") == task
        container.findByName("task") == task
    }

    void "finds task by relative path"() {
        when:
        Task task = task("task")
        expectTaskLookupInOtherProject("sub", "task", task)

        then:
        container.findByPath("sub:task") == task
    }

    void "finds tasks by absolute path"() {
        when:
        Task task = task("task")
        expectTaskLookupInOtherProject(":", "task", task)

        then:
        container.findByPath(":task") == task
    }

    void "does not find tasks from unknown projects"() {
        when:
        project.findProject(":unknown") >> null

        then:
        container.findByPath(":unknown:task") == null
    }

    void "searching by path ensures the other project is evaluated"() {
        given:
        def otherProject = expectTaskLookupInOtherProject(":other", "task", null)

        when:
        container.findByPath(":other:task")

        then:
        1 * otherProject.ensureEvaluated()
    }

    void "does not find unknown tasks by path"() {
        when:
        expectTaskLookupInOtherProject(":other", "task", null)

        then:
        container.findByPath(":other:task") >> null
    }

    void "gets task by path"() {
        when:
        Task task = addTask("task")
        expectTaskLookupInOtherProject(":a:b:c", "task", task)

        then:
        container.getByPath(":a:b:c:task") == task
    }

    void "get by path fails for unknown task"() {
        when:
        container.getByPath("unknown")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with path 'unknown' not found in Mock for type 'ProjectInternal' named '<project>'."
    }

    void "resolve locates by name"() {
        when:
        Task task = addTask("1")

        then:
        container.resolveTask("1") == task
    }

    void "resolve locates by path"() {
        when:
        Task task = addTask("task")
        expectTaskLookupInOtherProject(":", "task", task)

        then:
        container.resolveTask(new StringBuilder(":task")) == task
    }

    void "actualizes task graph"() {
        given:
        def task = addTask("a")
        def aTaskDependency = Mock(TaskDependency)
        task.getTaskDependencies() >> aTaskDependency

        def Task b = this.task("b")
        taskFactory.createTask(singletonMap(Task.TASK_NAME, "b")) >> b

        def bTaskDependency = Mock(TaskDependency, name: "bTaskDependency")
        b.getTaskDependencies() >> bTaskDependency

        bTaskDependency.getDependencies(b) >> Collections.emptySet()

        aTaskDependency.getDependencies(task) >> { container.add("b"); Collections.singleton(b) }
        task.dependsOn("b")

        assert container.size() == 1

        when:
        container.actualize()

        then:
        container.size() == 2
    }

    private ProjectInternal expectTaskLookupInOtherProject(final String projectPath, final String taskName, def task) {
        def otherProject = Mock(ProjectInternal)
        def otherTaskContainer = Mock(TaskContainerInternal)

        project.findProject(projectPath) >> otherProject
        otherProject.getTasks() >> otherTaskContainer

        otherTaskContainer.findByName(taskName) >> task

        otherProject
    }

    private TaskInternal task(final String name) {
        Mock(TaskInternal, name: "[task" + ++taskCount + "]") {
            getName() >> name
        }
    }

    private Task addTask(String name) {
        def task = task(name)
        def options = singletonMap(Task.TASK_NAME, name)
        taskFactory.createTask(options) >> task
        container.add(name)
        return task;
    }
}
