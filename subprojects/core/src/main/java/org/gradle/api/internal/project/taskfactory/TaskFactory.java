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

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.util.NameValidator;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

public class TaskFactory implements ITaskFactory {
    private final ProjectInternal project;
    private final InstantiationScheme instantiationScheme;

    public TaskFactory() {
        this(null, null);
    }

    private TaskFactory(ProjectInternal project, InstantiationScheme instantiationScheme) {
        this.project = project;
        this.instantiationScheme = instantiationScheme;
    }

    @Override
    public ITaskFactory createChild(ProjectInternal project, InstantiationScheme instantiationScheme) {
        return new TaskFactory(project, instantiationScheme);
    }

    @Override
    public <S extends Task> S create(final TaskIdentity<S> identity, @Nullable final Object[] constructorArgs) {
        if (!Task.class.isAssignableFrom(identity.type)) {
            throw new InvalidUserDataException(String.format(
                "Cannot create task of type '%s' as it does not implement the Task interface.",
                identity.type.getSimpleName()));
        }

        NameValidator.validate(identity.name, "task name", "");

        final Class<? extends AbstractTask> implType;
        if (identity.type.isAssignableFrom(DefaultTask.class)) {
            implType = DefaultTask.class;
        } else {
            implType = identity.type.asSubclass(AbstractTask.class);
        }

        return AbstractTask.injectIntoNewInstance(project, identity, new Callable<S>() {
            @Override
            public S call() {
                try {
                    Task instance;
                    if (constructorArgs != null) {
                        instance = instantiationScheme.instantiator().newInstance(implType, constructorArgs);
                    } else {
                        instance = instantiationScheme.deserializationInstantiator().newInstance(implType, AbstractTask.class);
                    }
                    return identity.type.cast(instance);
                } catch (ObjectInstantiationException e) {
                    throw new TaskInstantiationException(String.format("Could not create task of type '%s'.", identity.type.getSimpleName()),
                        e.getCause());
                }
            }
        });
    }
}
