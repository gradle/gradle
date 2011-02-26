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
package org.gradle.api.internal.project.taskfactory;

import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.util.GUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Hans Dockter
 */
public class TaskFactory implements ITaskFactory {
    public static final String GENERATE_SUBCLASS = "generateSubclass";
    private final ClassGenerator generator;

    public TaskFactory(ClassGenerator generator) {
        this.generator = generator;
    }

    public TaskInternal createTask(ProjectInternal project, Map<String, ?> args) {
        Map<String, Object> actualArgs = new HashMap<String, Object>(args);
        checkTaskArgsAndCreateDefaultValues(actualArgs);

        String name = actualArgs.get(Task.TASK_NAME).toString();
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The task name must be provided.");
        }

        Class<? extends TaskInternal> type = (Class) actualArgs.get(Task.TASK_TYPE);
        Boolean generateSubclass = Boolean.valueOf(actualArgs.get(GENERATE_SUBCLASS).toString());
        TaskInternal task = createTaskObject(project, type, name, generateSubclass);

        Object dependsOnTasks = actualArgs.get(Task.TASK_DEPENDS_ON);
        if (dependsOnTasks != null) {
            task.dependsOn(dependsOnTasks);
        }
        Object description = actualArgs.get(Task.TASK_DESCRIPTION);
        if (description != null) {
            task.setDescription(description.toString());
        }
        Object group = actualArgs.get(Task.TASK_GROUP);
        if (group != null) {
            task.setGroup(group.toString());
        }
        Object action = actualArgs.get(Task.TASK_ACTION);
        if (action instanceof Action) {
            Action<? super Task> taskAction = (Action<? super Task>) action;
            task.doFirst(taskAction);
        } else if (action != null) {
            Closure closure = (Closure) action;
            task.doFirst(closure);
        }

        return task;
    }

    private TaskInternal createTaskObject(ProjectInternal project, final Class<? extends TaskInternal> type, String name, boolean generateGetters) {
        if (!Task.class.isAssignableFrom(type)) {
            throw new InvalidUserDataException(String.format(
                    "Cannot create task of type '%s' as it does not implement the Task interface.",
                    type.getSimpleName()));
        }

        Class<? extends TaskInternal> generatedType;
        if (generateGetters) {
            generatedType = generator.generate(type);
        } else {
            generatedType = type;
        }

        final Constructor<? extends TaskInternal> constructor;
        final Object[] params;
        try {
            constructor = generatedType.getDeclaredConstructor();
            params = new Object[0];
        } catch (NoSuchMethodException e) {
            // Ignore
            throw new InvalidUserDataException(String.format(
                    "Cannot create task of type '%s' as it does not have a public no-args constructor.",
                    type.getSimpleName()));
        }

        return AbstractTask.injectIntoNewInstance(project, name, new Callable<TaskInternal>() {
            public TaskInternal call() throws Exception {
                try {
                    return constructor.newInstance(params);
                } catch (InvocationTargetException e) {
                    throw new TaskInstantiationException(String.format("Could not create task of type '%s'.", type.getSimpleName()),
                            e.getCause());
                } catch (Exception e) {
                    throw new TaskInstantiationException(String.format("Could not create task of type '%s'.", type.getSimpleName()), e);
                }
            }
        });
    }

    private void checkTaskArgsAndCreateDefaultValues(Map<String, Object> args) {
        setIfNull(args, Task.TASK_NAME, "");
        setIfNull(args, Task.TASK_TYPE, DefaultTask.class);
        if (((Class) args.get(Task.TASK_TYPE)).isAssignableFrom(DefaultTask.class)) {
            args.put(Task.TASK_TYPE, DefaultTask.class);
        }
        setIfNull(args, GENERATE_SUBCLASS, "true");
    }

    private void setIfNull(Map<String, Object> map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }
}
