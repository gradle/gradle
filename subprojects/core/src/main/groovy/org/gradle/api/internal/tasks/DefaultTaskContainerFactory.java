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

import org.gradle.api.Project;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;

public class DefaultTaskContainerFactory implements Factory<TaskContainerInternal> {
    private final Instantiator instantiator;
    private final ITaskFactory taskFactory;
    private Project project;
    public ProjectAccessListener projectAccessListener;

    public DefaultTaskContainerFactory(Instantiator instantiator, ITaskFactory taskFactory, Project project, ProjectAccessListener projectAccessListener) {
        this.instantiator = instantiator;
        this.taskFactory = taskFactory;
        this.project = project;
        this.projectAccessListener = projectAccessListener;
    }

    public TaskContainerInternal create() {
        return instantiator.newInstance(DefaultTaskContainer.class, project, instantiator, taskFactory, projectAccessListener);
    }
}
