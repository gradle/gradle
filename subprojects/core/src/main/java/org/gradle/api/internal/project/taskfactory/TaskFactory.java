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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GUtil;
import org.gradle.util.NameValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class TaskFactory implements ITaskFactory {
    private static final Set<String> VALID_TASK_ARGUMENTS = ImmutableSet.of(
        Task.TASK_ACTION, Task.TASK_DEPENDS_ON, Task.TASK_DESCRIPTION, Task.TASK_GROUP, Task.TASK_NAME, Task.TASK_OVERWRITE, Task.TASK_TYPE
    );
    private static final Set<String> MANDATORY_TASK_ARGUMENTS = ImmutableSet.of(
        Task.TASK_NAME, Task.TASK_TYPE
    );
    private final ClassGenerator generator;
    private final ProjectInternal project;
    private final Instantiator instantiator;

    public TaskFactory(ClassGenerator generator) {
        this(generator, null, null);
    }

    TaskFactory(ClassGenerator generator, ProjectInternal project, Instantiator instantiator) {
        this.generator = generator;
        this.project = project;
        this.instantiator = instantiator;
    }

    public ITaskFactory createChild(ProjectInternal project, Instantiator instantiator) {
        return new TaskFactory(generator, project, instantiator);
    }

    public TaskInternal createTask(Map<String, ?> args) {
        Map<String, ?> actualArgs = checkTaskArgsAndCreateDefaultValues(args);

        String name = actualArgs.get(Task.TASK_NAME).toString();
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The task name must be provided.");
        }

        Class<? extends TaskInternal> type = (Class) actualArgs.get(Task.TASK_TYPE);
        TaskInternal task = create(name, type);

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

    @Override
    public <S extends TaskInternal> S create(String name, final Class<S> type) {
        if (!Task.class.isAssignableFrom(type)) {
            throw new InvalidUserDataException(String.format(
                "Cannot create task of type '%s' as it does not implement the Task interface.",
                type.getSimpleName()));
        }
        NameValidator.validate(name, "task name", "");

        final Class<? extends Task> generatedType;
        if (type.isAssignableFrom(DefaultTask.class)) {
            generatedType = generator.generate(DefaultTask.class);
        } else {
            generatedType = generator.generate(type);
        }

        return type.cast(AbstractTask.injectIntoNewInstance(project, name, type, new Callable<Task>() {
            public Task call() throws Exception {
                try {
                    return instantiator.newInstance(generatedType);
                } catch (ObjectInstantiationException e) {
                    throw new TaskInstantiationException(String.format("Could not create task of type '%s'.", type.getSimpleName()),
                        e.getCause());
                }
            }
        }));
    }

    private Map<String, ?> checkTaskArgsAndCreateDefaultValues(Map<String, ?> args) {
        validateArgs(args);
        if (!args.keySet().containsAll(MANDATORY_TASK_ARGUMENTS)) {
            Map<String, Object> argsWithDefaults = Maps.newHashMap(args);
            setIfNull(argsWithDefaults, Task.TASK_NAME, "");
            setIfNull(argsWithDefaults, Task.TASK_TYPE, DefaultTask.class);
            return argsWithDefaults;
        }
        return args;
    }

    private void validateArgs(Map<String, ?> args) {
        if (!VALID_TASK_ARGUMENTS.containsAll(args.keySet())) {
            Map unknownArguments = new HashMap<String, Object>(args);
            unknownArguments.keySet().removeAll(VALID_TASK_ARGUMENTS);
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
