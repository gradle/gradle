/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.*;
import org.gradle.api.internal.DefaultTask;
import org.gradle.util.GUtil;
import org.gradle.execution.Dag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.lang.reflect.Constructor;

import groovy.lang.Closure;
import groovy.lang.GString;

/**
 * @author Hans Dockter
 */
public class TaskFactory implements ITaskFactory {
    private static Logger logger = LoggerFactory.getLogger(TaskFactory.class);

    private Dag tasksGraph;

    public TaskFactory(Dag tasksGraph) {
        this.tasksGraph = tasksGraph;
    }

    public Task createTask(Project project, Map tasksMap, Map args, String name) {
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The name of the task must be set!");
        }
        checkTaskArgsAndCreateDefaultValues(args);
        if (!Boolean.valueOf(args.get(Task.TASK_OVERWRITE).toString()) && tasksMap.get(name) != null) {
            throw new InvalidUserDataException("A task with this name already exists!");
        }
        Task task = createTaskObject(project, (Class) args.get(Task.TASK_TYPE), name);
        Object dependsOn = args.get(Task.TASK_DEPENDS_ON);
        if (dependsOn instanceof String || (dependsOn instanceof GString)) {
            String singleDependencyName = (String) dependsOn;
            if (singleDependencyName == null) {
                throw new InvalidUserDataException("A dependency name must not be empty!");
            }
            args.put(Task.TASK_DEPENDS_ON, Collections.singletonList(singleDependencyName));
        }
        Object dependsOnTasks = args.get(Task.TASK_DEPENDS_ON);
        if (logger.isDebugEnabled()) {
            logger.debug("Adding dependencies: " + dependsOnTasks);
        }

        task.dependsOn(dependsOnTasks);
        
        return task;
    }

    private Task createTaskObject(Project project, Class type, String name) {
        // todo - remove this one
        try {
            Constructor constructor = type.getDeclaredConstructor(Project.class, String.class, Dag.class);
            return (Task) constructor.newInstance(project, name, tasksGraph);
        } catch (NoSuchMethodException e) {
            // ignore, try next one
        } catch (Exception e) {
            throw new GradleException("Task creation error.", e);
        }

        try {
            Constructor constructor = type.getDeclaredConstructor(Project.class, String.class);
            return (Task) constructor.newInstance(project, name);
        } catch (Exception e) {
            throw new GradleException("Task creation error.", e);
        }
    }

    private void checkTaskArgsAndCreateDefaultValues(Map args) {
        setIfNull(args, Task.TASK_TYPE, DefaultTask.class);
        setIfNull(args, Task.TASK_DEPENDS_ON, new ArrayList());
        setIfNull(args, Task.TASK_OVERWRITE, new Boolean(false));
    }

    private void setIfNull(Map map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }

    public Dag getTasksGraph() {
        return tasksGraph;
    }

    public void setTasksGraph(Dag tasksGraph) {
        this.tasksGraph = tasksGraph;
    }
}
