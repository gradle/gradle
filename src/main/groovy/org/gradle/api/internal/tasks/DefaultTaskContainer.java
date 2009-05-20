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
import org.gradle.api.internal.AbstractDomainObjectCollection;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.internal.project.ITaskFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GUtil;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class DefaultTaskContainer extends DefaultDomainObjectContainer<Task> implements TaskContainer {
    private final Project project;
    private final ITaskFactory taskFactory;

    public DefaultTaskContainer(Project project, ITaskFactory taskFactory) {
        super(Task.class);
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
            throw new InvalidUserDataException(String.format(
                    "Cannot add task '%s' as a task with that name already exists.", name));
        }

        addObject(name, task);

        return task;
    }

    public Task add(Map<String, ?> options, Closure configureClosure) throws InvalidUserDataException {
        return add(options).configure(configureClosure);
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

    public Task add(String name, Closure configureClosure) {
        return add(GUtil.map(Task.TASK_NAME, name)).configure(configureClosure);
    }

    public <T extends Task> T replace(String name, Class<T> type) {
        return type.cast(add(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type, Task.TASK_OVERWRITE, true)));
    }

    @Override
    public FilteredTaskCollection<Task> matching(Spec<? super Task> spec) {
        return new FilteredTaskCollection<Task>(this, Task.class, spec);
    }

    @Override
    public <T extends Task> FilteredTaskCollection<T> withType(Class<T> type) {
        return new FilteredTaskCollection<T>(this, type, Specs.SATISFIES_ALL);
    }

    public Action<? super Task> whenTaskAdded(Action<? super Task> action) {
        return whenObjectAdded(action);
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

    public Task findByPath(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!path.contains(Project.PATH_SEPARATOR)) {
            return findByName(path);
        }

        String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
        Project project = this.project.findProject(!GUtil.isTrue(projectPath) ? Project.PATH_SEPARATOR : projectPath);
        if (project == null) {
            return null;
        }
        return project.getTasks().findByName(StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR));
    }

    public Task getByPath(String path) throws UnknownTaskException {
        Task task = findByPath(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
        }
        return task;
    }

    @Override
    public String getDisplayName() {
        return "task container";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownTaskException(String.format("Task with name '%s' not found in %s.", name, project));
    }

    private static class FilteredTaskCollection<T extends Task> extends FilteredContainer<T> implements TaskCollection<T> {
        private FilteredTaskCollection(AbstractDomainObjectCollection<? super T> parent, Class<T> type, Spec<? super T> spec) {
            super(parent, type, spec);
        }

        @Override
        public FilteredTaskCollection<T> matching(Spec<? super T> spec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends T> FilteredTaskCollection<S> withType(Class<S> type) {
            throw new UnsupportedOperationException();
        }

        public void allTasks(Action<? super T> action) {
            allObjects(action);
        }

        public void allTasks(Closure closure) {
            allObjects(closure);
        }

        public Action<? super T> whenTaskAdded(Action<? super T> action) {
            return whenObjectAdded(action);
        }

        public void whenTaskAdded(Closure closure) {
            whenObjectAdded(closure);
        }
    }
}
