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

package org.gradle.api.internal.tasks;

import org.gradle.api.Project;

import javax.annotation.Nullable;

/**
 * A self-contained action to run as a node in the work graph. Can be included in the dependencies of a {@link TaskDependencyContainer}.
 */
public interface WorkNodeAction {
    /**
     * Does this action require exclusive access to the mutable state of its owning project?
     */
    default boolean usesMutableProjectState() {
        return false;
    }

    /**
     * Returns the project which the action belongs to. This is used to determine the services to expose to {@link #run(NodeExecutionContext)} via the context.
     * Returning non-null here does not imply any kind of exclusive access to the project, unless {@link #usesMutableProjectState()} returns true.
     */
    @Nullable
    default Project getOwningProject() {
        return null;
    }

    default void visitDependencies(TaskDependencyResolveContext context) {
    }

    /**
     * Run the action, throwing any failure.
     */
    void run(NodeExecutionContext context);

    @Nullable
    default WorkNodeAction getPreExecutionNode() {
        return null;
    }
}
