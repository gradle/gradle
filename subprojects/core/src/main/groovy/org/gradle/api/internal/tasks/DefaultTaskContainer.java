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

import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.CachingDirectedGraphWalker;
import org.gradle.api.internal.DirectedGraph;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.NamedDomainObjectContainerConfigureDelegate;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultTaskContainer extends DefaultTaskCollection<Task> implements TaskContainerInternal {
    private final ITaskFactory taskFactory;
    private final ProjectAccessListener projectAccessListener;

    public DefaultTaskContainer(ProjectInternal project, Instantiator instantiator, ITaskFactory taskFactory, ProjectAccessListener projectAccessListener) {
        super(Task.class, instantiator, project);
        this.taskFactory = taskFactory;
        this.projectAccessListener = projectAccessListener;
    }

    public Task add(Map<String, ?> options) {
        Map<String, Object> mutableOptions = new HashMap<String, Object>(options);

        Object replaceStr = mutableOptions.remove(Task.TASK_OVERWRITE);
        boolean replace = replaceStr != null && "true".equals(replaceStr.toString());

        Task task = taskFactory.createTask(mutableOptions);
        String name = task.getName();

        Task existing = findByNameWithoutRules(name);
        if (existing != null) {
            if (replace) {
                remove(existing);
            } else {
                throw new InvalidUserDataException(String.format(
                        "Cannot add %s as a task with that name already exists.", task));
            }
        }

        add(task);

        return task;
    }

    public Task add(Map<String, ?> options, Closure configureClosure) throws InvalidUserDataException {
        return add(options).configure(configureClosure);
    }

    public <T extends Task> T add(String name, Class<T> type) {
        return type.cast(add(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type)));
    }

    public Task create(String name) {
        return add(name);
    }

    public Task maybeCreate(String name) {
        Task task = findByName(name);
        if (task != null) {
            return task;
        }
        return create(name);
    }

    public Task add(String name) {
        return add(GUtil.map(Task.TASK_NAME, name));
    }

    public Task replace(String name) {
        return add(GUtil.map(Task.TASK_NAME, name, Task.TASK_OVERWRITE, true));
    }

    public Task create(String name, Closure configureClosure) {
        return add(name, configureClosure);
    }

    public Task add(String name, Closure configureClosure) {
        return add(GUtil.map(Task.TASK_NAME, name)).configure(configureClosure);
    }

    public <T extends Task> T replace(String name, Class<T> type) {
        return type.cast(add(GUtil.map(Task.TASK_NAME, name, Task.TASK_TYPE, type, Task.TASK_OVERWRITE, true)));
    }

    public Task findByPath(String path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if (!path.contains(Project.PATH_SEPARATOR)) {
            return findByName(path);
        }

        String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
        ProjectInternal project = this.project.findProject(!GUtil.isTrue(projectPath) ? Project.PATH_SEPARATOR : projectPath);
        if (project == null) {
            return null;
        }
        projectAccessListener.beforeRequestingTaskByPath(project);

        return project.getTasks().findByName(StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR));
    }

    public Task resolveTask(Object path) {
        if (!GUtil.isTrue(path)) {
            throw new InvalidUserDataException("A path must be specified!");
        }
        if(!(path instanceof CharSequence)) {
            DeprecationLogger.nagUserOfDeprecated(
                    String.format("Converting class %s to a task dependency using toString()", path.getClass().getName()),
                    "Please use org.gradle.api.Task, java.lang.String, org.gradle.api.Buildable, org.gradle.tasks.TaskDependency or a Closure to declare your task dependencies"
            );
        }
        return getByPath(path.toString());
    }

    public Task getByPath(String path) throws UnknownTaskException {
        Task task = findByPath(path);
        if (task == null) {
            throw new UnknownTaskException(String.format("Task with path '%s' not found in %s.", path, project));
        }
        return task;
    }

    protected Object createConfigureDelegate(Closure configureClosure) {
        return new NamedDomainObjectContainerConfigureDelegate(configureClosure.getOwner(), this);
    }

    public TaskContainerInternal configure(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, createConfigureDelegate(configureClosure));
        return this;
    }

    public DynamicObject getTasksAsDynamicObject() {
        return getElementsAsDynamicObject();
    }

    public void actualize() {
        new CachingDirectedGraphWalker<Task, Void>(new DirectedGraph<Task, Void>() {
            public void getNodeValues(Task node, Collection<Void> values, Collection<Task> connectedNodes) {
                connectedNodes.addAll(node.getTaskDependencies().getDependencies(node));
            }
        }).add(this).findValues();
    }
}
