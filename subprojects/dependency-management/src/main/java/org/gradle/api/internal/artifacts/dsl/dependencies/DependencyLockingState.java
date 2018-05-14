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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.DependencyConstraint;

import java.util.Set;

/**
 * Describes the lock state for a given configuration.
 */
public interface DependencyLockingState {

    /**
     * Indicates if the lock state must be validated
     *
     * @return {@code true} if lock state was found, {@code false} otherwise
     */
    boolean mustValidateLockState();

    /**
     * Returns the set of locking constraints found.
     * Note that an empty set can mean either that lock state was not defined or that lock state is an empty set.
     * Disambiguation can be done by calling {@link #mustValidateLockState()}.
     *
     * @return a set of constraints
     * @see #mustValidateLockState()
     */
    Set<DependencyConstraint> getLockedDependencies();
}
