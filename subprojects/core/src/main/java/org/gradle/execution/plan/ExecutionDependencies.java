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

package org.gradle.execution.plan;

import org.gradle.api.Task;

import java.util.Set;

public interface ExecutionDependencies {

    /**
     * Returns the set of task dependencies.
     *
     * @return The tasks. Returns an empty set if there are no dependent tasks.
     */
    Set<Task> getTasks();

    /**
     * Returns the set of transformation dependencies.
     *
     * @return The transformations. Returns an empty set if there are no dependent transformation.
     */
    Set<TransformationNodeIdentifier> getTransformations();

}
