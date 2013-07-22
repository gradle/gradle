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
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.util.GUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class TaskFactory implements ITaskFactory {
    public static final String GENERATE_SUBCLASS = "generateSubclass";
    private final ClassGenerator generator;
    private final ProjectInternal project;
    private final Instantiator instantiator;
    private final Set<String> validTaskArguments;

    public TaskFactory(ClassGenerator generator) {
        this(generator, null, null);
    }

    TaskFactory(ClassGenerator generator, ProjectInternal project, Instantiator instantiator) {
        this.generator = generator;
        this.project = project;
        this.instantiator = instantiator;


        validTaskArguments = new HashSet<String>();
        validTaskArguments.add(Task.TASK_ACTION);
        validTaskArguments.add(Task.TASK_DEPENDS_ON);
        validTaskArguments.add(Task.TASK_DESCRIPTION);
        validTaskArguments.add(Task.TASK_GROUP);
        validTaskArguments.add(Task.TASK_NAME);
        validTaskArguments.add(Task.TASK_OVERWRITE);
        validTaskArguments.add(Task.TASK_TYPE);

    }

    public ITaskFactory createChild(ProjectInternal project, Instantiator instantiator) {
        return new TaskFactory(generator, project, instantiator);
    }

    public TaskInternal createTask(Map<String, ?> args) {
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

        final Class<? extends TaskInternal> generatedType;
        if (generateGetters) {
            generatedType = generator.generate(type);
        } else {
            generatedType = type;
        }

        return AbstractTask.injectIntoNewInstance(project, name, new Callable<TaskInternal>() {
            public TaskInternal call() throws Exception {
                try {
                    return instantiator.newInstance(generatedType);
                } catch (ObjectInstantiationException e) {
                    throw new TaskInstantiationException(String.format("Could not create task of type '%s'.", type.getSimpleName()),
                            e.getCause());
                }
            }
        });
    }

    private void checkTaskArgsAndCreateDefaultValues(Map<String, Object> args) {
        validateArgs(args);
        setIfNull(args, Task.TASK_NAME, "");
        setIfNull(args, Task.TASK_TYPE, DefaultTask.class);
        if (((Class) args.get(Task.TASK_TYPE)).isAssignableFrom(DefaultTask.class)) {
            args.put(Task.TASK_TYPE, DefaultTask.class);
        }
        setIfNull(args, GENERATE_SUBCLASS, "true");
    }

    private void validateArgs(Map<String, Object> args) {
        if (!validTaskArguments.containsAll(args.keySet())) {
            Map unknownArguments = new HashMap<String, Object>(args);
            unknownArguments.keySet().removeAll(validTaskArguments);
            throw new InvalidUserDataException(String.format("Could not create task '%s': Unknown argument(s) in task definition: %s",
                        args.get(Task.TASK_NAME), unknownArguments.keySet()));
        }
    }

    private void setIfNull(Map<String, Object> map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }
}
