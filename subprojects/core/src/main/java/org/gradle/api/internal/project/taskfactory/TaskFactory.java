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
import org.gradle.api.Describable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.internal.Describables;
import org.gradle.internal.deprecation.DeprecationLogger;
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
    @SuppressWarnings("deprecation")
    public <S extends Task> S create(final TaskIdentity<S> identity, @Nullable final Object[] constructorArgs) {
        if (!Task.class.isAssignableFrom(identity.type)) {
            throw new InvalidUserDataException(String.format(
                "Cannot create task of type '%s' as it does not implement the Task interface.",
                identity.type.getSimpleName()));
        }

        NameValidator.validate(identity.name, "task name", "");

        final Class<? extends org.gradle.api.internal.AbstractTask> implType;
        if (identity.type == Task.class) {
            implType = DefaultTask.class;
        } else if (DefaultTask.class.isAssignableFrom(identity.type)) {
            implType = identity.type.asSubclass(org.gradle.api.internal.AbstractTask.class);
        } else if (identity.type == org.gradle.api.internal.AbstractTask.class || identity.type == TaskInternal.class) {
            DeprecationLogger.deprecate(String.format("Registering task '%s' with type '%s'", identity.identityPath.toString(), identity.type.getSimpleName()))
                .willBecomeAnErrorInGradle7()
                .withUpgradeGuideSection(6, "abstract_task_deprecated")
                .nagUser();
            implType = DefaultTask.class;
        } else {
            DeprecationLogger.deprecate(String.format("Registering task '%s' with a type (%s) that directly extends AbstractTask", identity.identityPath.toString(), identity.type.getSimpleName()))
                .willBecomeAnErrorInGradle7()
                .withUpgradeGuideSection(6, "abstract_task_deprecated")
                .nagUser();
            implType = identity.type.asSubclass(org.gradle.api.internal.AbstractTask.class);
        }

        Describable displayName = Describables.withTypeAndName("task", identity.getIdentityPath());

        return org.gradle.api.internal.AbstractTask.injectIntoNewInstance(project, identity, new Callable<S>() {
            @Override
            public S call() {
                try {
                    Task instance;
                    if (constructorArgs != null) {
                        instance = instantiationScheme.instantiator().newInstanceWithDisplayName(implType, displayName, constructorArgs);
                    } else {
                        instance = instantiationScheme.deserializationInstantiator().newInstance(implType, org.gradle.api.internal.AbstractTask.class);
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
