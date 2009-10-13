/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ITaskFactory} which short-circuits the execution of a task when its inputs have not changed since its
 * outputs where generated.
 */
public class ExecutionShortCircuitTaskFactory implements ITaskFactory {
    public static String EXECUTION_SHORT_CIRCUIT = "executionShortCircuit";
    private final ITaskFactory taskFactory;
    private final TaskArtifactStateRepository repository;

    public ExecutionShortCircuitTaskFactory(ITaskFactory taskFactory, TaskArtifactStateRepository repository) {
        this.taskFactory = taskFactory;
        this.repository = repository;
    }

    public TaskInternal createTask(ProjectInternal project, Map<String, ?> args) {
        Map<String, Object> actualArgs = new HashMap<String, Object>(args);
        boolean shortCircuit = remove(actualArgs, EXECUTION_SHORT_CIRCUIT);

        TaskInternal task = taskFactory.createTask(project, actualArgs);
        if (shortCircuit) {
            task.setExecuter(new ExecutionShortCircuitTaskExecuter(task.getExecuter(), repository));
        }
        return task;
    }

    private boolean remove(Map<String, ?> args, String key) {
        Object value = args.remove(key);
        return value == null ? true : Boolean.valueOf(value.toString());
    }
}
