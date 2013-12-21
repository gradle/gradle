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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;

import java.util.Map;

/**
 * A {@link ITaskFactory} which wires the dependencies of a task based on its input files.
 */
public class DependencyAutoWireTaskFactory implements ITaskFactory {
    private final ITaskFactory taskFactory;

    public DependencyAutoWireTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public ITaskFactory createChild(ProjectInternal project, Instantiator instantiator) {
        return new DependencyAutoWireTaskFactory(taskFactory.createChild(project, instantiator));
    }

    public TaskInternal createTask(Map<String, ?> args) {
        TaskInternal task = taskFactory.createTask(args);
        task.dependsOn(task.getInputs().getFiles());
        return task;
    }
}
