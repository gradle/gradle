/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.registration;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.features.registration.TaskRegistrar;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Default implementation of TaskRegistrar, which delegates to a TaskContainer.
 */
// TODO - Implement context-specific naming
public class DefaultTaskRegistrar implements TaskRegistrar {
    private final TaskContainer taskContainer;

    public DefaultTaskRegistrar(TaskContainer taskContainer) {
        this.taskContainer = taskContainer;
    }

    @Override
    public TaskProvider<Task> register(String name, Action<? super Task> configurationAction) throws InvalidUserDataException {
        return taskContainer.register(name, configurationAction);
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type, Action<? super T> configurationAction) throws InvalidUserDataException {
        return taskContainer.register(name, type, configurationAction);
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type) throws InvalidUserDataException {
        return taskContainer.register(name, type);
    }

    @Override
    public <T extends Task> TaskProvider<T> register(String name, Class<T> type, Object... constructorArgs) throws InvalidUserDataException {
        return taskContainer.register(name, type, constructorArgs);
    }

    @Override
    public TaskProvider<Task> register(String name) throws InvalidUserDataException {
        return taskContainer.register(name);
    }
}
