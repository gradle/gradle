/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.model;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.WorkNodeAction;

/**
 * Produces a value, potentially based on values produced by tasks or other work nodes.
 */
public interface ValueCalculator<T> extends TaskDependencyContainer {
    /**
     * See {@link WorkNodeAction#usesMutableProjectState()}.
     */
    boolean usesMutableProjectState();

    /**
     * See {@link WorkNodeAction#getOwningProject()}.
     */
    ProjectInternal getOwningProject();

    T calculateValue(NodeExecutionContext context);
}
