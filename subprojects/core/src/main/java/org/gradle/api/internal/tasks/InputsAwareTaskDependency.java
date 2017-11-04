/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.tasks.TaskInputs;

/**
 * A task dependency that is aware of the inputs of the task that owns it.
 * No matter what the user does with the dependencies, the task should always
 * depend on its own inputs.
 */
public class InputsAwareTaskDependency extends DefaultTaskDependency {
    private final TaskInputs inputs;

    public InputsAwareTaskDependency(TaskInputs inputs, TaskResolver resolver) {
        super(resolver);
        this.inputs = inputs;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(inputs.getFiles());
        super.visitDependencies(context);
    }
}
