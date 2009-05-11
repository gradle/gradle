/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.internal.project.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GUtil;

import java.util.HashMap;
import java.util.Map;

public class DefaultTaskContainer extends DefaultDomainObjectContainer<Task> implements TaskContainer {
    private final Project project;
    private final ITaskFactory taskFactory;

    public DefaultTaskContainer(Project project, ITaskFactory taskFactory) {
        this.project = project;
        this.taskFactory = taskFactory;
    }

    public Task add(Map<String, ?> options) {
        Map<String, Object> mutableOptions = new HashMap<String, Object>(options);
        
        Object replaceStr = mutableOptions.remove(Task.TASK_OVERWRITE);
        boolean replace = replaceStr != null && "true".equals(replaceStr.toString());

        Task task = taskFactory.createTask(project, mutableOptions);
        String name = task.getName();

        if (!replace && findByName(name) != null) {
            throw new InvalidUserDataException(String.format( "Cannot add task '%s' as a task with that name already exists.", name));
        }

        addObject(name, task);

        return task;
    }

    public Task add(Map<String, ?> options, Closure taskAction) throws InvalidUserDataException {
        return add(GUtil.addMaps(options, GUtil.map(Task.TASK_ACTION, taskAction)));
    }

    public <T extends Task> T add(String name, Class<T> type) {
        return type.cast(add(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type)));
    }

    public Task add(String name) {
        return add(GUtil.map(Task.TASK_NAME, name));
    }

    public Task replace(String name) {
        return add(GUtil.map(Task.TASK_NAME, name, Task.TASK_OVERWRITE, true));
    }

    public Task add(String name, TaskAction taskAction) {
        return add(GUtil.map(Task.TASK_NAME, name, Task.TASK_ACTION, taskAction));
    }

    public Task add(String name, Closure taskAction) {
        return add(GUtil.map(Task.TASK_NAME, name, Task.TASK_ACTION, taskAction));
    }

    public <T extends Task> T replace(String name, Class<T> type) {
        return type.cast(add(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type, Task.TASK_OVERWRITE, true)));
    }

    @Override
    protected void addObject(String name, Task object) {
        super.addObject(name, object);
    }

    public Action<? super Task> whenTaskAdded(Action<? super Task> action) {
        return whenObjectAdded(action);
    }

    public <T extends Task> Action<T> whenTaskAdded(final Class<T> type, final Action<T> action) {
        whenTaskAdded(new Action<Task>() {
            public void execute(Task task) {
                if (type.isInstance(task)) {
                    action.execute(type.cast(task));
                }
            }
        });
        return action;
    }

    public void whenTaskAdded(Closure closure) {
        whenObjectAdded(closure);
    }

    public void allTasks(Action<? super Task> action) {
        allObjects(action);
    }

    public void allTasks(Closure action) {
        allObjects(action);
    }

    @Override
    public String getDisplayName() {
        return "task container";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownTaskException(String.format("Task with name '%s' not found.", name));
    }
}
