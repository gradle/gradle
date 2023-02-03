/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.hash.Hasher;

public interface BuildActionModelRequirements {
    /**
     * Will the action run tasks?
     */
    boolean isRunsTasks();

    /**
     * Will the action create a tooling model? Note that actions can both run tasks and create a tooling model.
     */
    boolean isCreatesModel();

    StartParameterInternal getStartParameter();

    DisplayName getActionDisplayName();

    /**
     * A description of the important components of the cache key for this action.
     */
    DisplayName getConfigurationCacheKeyDisplayName();

    /**
     * Appends any additional values that should contribute to the configuration cache entry key for this action.
     * Should not append any details of the requested tasks, as these are always added when {@link #isRunsTasks()} returns true.
     */
    void appendKeyTo(Hasher hasher);
}
