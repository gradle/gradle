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

package org.gradle.api.internal.artifacts.configurations;

/**
 * Some value that is calculated as part of dependency resolution, but which may have a partial or different value
 * when the execution graph is calculated.
 * @param <T>
 */
public interface ResolutionResultProvider<T> {
    /**
     * Returns the value available at execution graph calculation time. Note that the value may change between when the execution graph is calculated and
     * when the final value is calculated. For example, only project dependencies may be included in a dependency graph that is used to calculate the task
     * dependencies, and the full dependency graph calculated later at task execution time.
     */
    T getTaskDependencyValue();

    /**
     * Returns the finalized value.
     */
    T getValue();
}
