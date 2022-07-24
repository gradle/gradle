/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.model.internal.core.NamedEntityInstantiator;

public class TaskInstantiator implements NamedEntityInstantiator<Task> {
    private static final Object[] NO_PARAMS = new Object[0];
    private final ITaskFactory taskFactory;
    private final ProjectInternal project;

    public TaskInstantiator(ITaskFactory taskFactory, ProjectInternal project) {
        this.taskFactory = taskFactory;
        this.project = project;
    }

    @Override
    public <S extends Task> S create(String name, Class<S> type) {
        return taskFactory.create(TaskIdentity.create(name, type, project), NO_PARAMS);
    }
}
