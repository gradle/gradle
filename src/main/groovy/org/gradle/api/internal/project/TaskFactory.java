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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public class TaskFactory implements ITaskFactory {
    public Task createTask(Project project, Map args) {
        checkTaskArgsAndCreateDefaultValues(args);

        String name = args.get(Task.TASK_NAME).toString();
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The task name must be provided.");
        }

        Task task = createTaskObject(project, (Class) args.get(Task.TASK_TYPE), name);

        Object dependsOnTasks = args.get(Task.TASK_DEPENDS_ON);
        task.dependsOn(dependsOnTasks);
        Object description = args.get(Task.TASK_DESCRIPTION);
        if (description != null) {
            task.setDescription(description.toString());
        }
        Object action = args.get(Task.TASK_ACTION);
        if (action instanceof TaskAction) {
            TaskAction taskAction = (TaskAction) action;
            task.doFirst(taskAction);
        }
        else if (action != null) {
            Closure closure = (Closure) action;
            task.doFirst(closure);
        }

        return task;
    }

    private Task createTaskObject(Project project, Class type, String name) {
        if (!Task.class.isAssignableFrom(type)) {
            throw new GradleException(String.format(
                    "Cannot create task of type '%s' as it does not implement the Task interface.",
                    type.getSimpleName()));
        }
        Constructor constructor;
        try {
            constructor = type.getDeclaredConstructor(Project.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new GradleException(String.format(
                    "Cannot create task of type '%s' as it does not have an appropriate public constructor.",
                    type.getSimpleName()));
        }

        try {
            return (Task) constructor.newInstance(project, name);
        } catch (InvocationTargetException e) {
            throw new GradleException(String.format("Could not create task of type '%s'.", type.getSimpleName()),
                    e.getCause());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create task of type '%s'.", type.getSimpleName()), e);
        }
    }

    private void checkTaskArgsAndCreateDefaultValues(Map args) {
        setIfNull(args, Task.TASK_NAME, "");
        setIfNull(args, Task.TASK_TYPE, DefaultTask.class);
        setIfNull(args, Task.TASK_DEPENDS_ON, new ArrayList());
    }

    private void setIfNull(Map map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }
}
