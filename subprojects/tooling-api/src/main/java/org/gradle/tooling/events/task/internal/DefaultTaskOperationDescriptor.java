/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events.task.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.model.internal.Exceptions;

import java.util.Set;

/**
 * Implementation of the {@code TaskOperationDescriptor} interface.
 */
public final class DefaultTaskOperationDescriptor extends DefaultOperationDescriptor implements TaskOperationDescriptor {

    private final String taskPath;
    private final Set<OperationDescriptor> dependencies;

    public DefaultTaskOperationDescriptor(InternalTaskDescriptor descriptor, OperationDescriptor parent, String taskPath, Set<OperationDescriptor> dependencies) {
        super(descriptor, parent);
        this.taskPath = taskPath;
        this.dependencies = dependencies;
    }

    @Override
    public String getTaskPath() {
        return taskPath;
    }

    @Override
    public Set<? extends OperationDescriptor> getDependencies() {
        if (dependencies == null) {
            throw Exceptions.unsupportedMethod(TaskOperationDescriptor.class.getSimpleName() + ".getDependencies()");
        }
        return dependencies;
    }

}
