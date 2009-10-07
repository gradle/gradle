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

import org.gradle.api.Task;
import org.gradle.api.Project;

import java.util.Map;
import java.util.HashMap;

public class DependencyAutoWireTaskFactory implements ITaskFactory {
    public static String DEPENDENCY_AUTO_WIRE = "dependencyAutoWire";
    private final ITaskFactory taskFactory;

    public DependencyAutoWireTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public Task createTask(Project project, Map<String, ?> args) {
        Map<String, Object> actualArgs = new HashMap<String, Object>(args);
        boolean autoWire = get(actualArgs, DEPENDENCY_AUTO_WIRE);

        Task task = taskFactory.createTask(project, actualArgs);
        if (autoWire) {
            task.dependsOn(task.getInputs().getInputFiles());
        }
        return task;
    }

    private boolean get(Map<String, ?> args, String key) {
        Object value = args.remove(key);
        return value == null ? true : Boolean.valueOf(value.toString());
    }
}
