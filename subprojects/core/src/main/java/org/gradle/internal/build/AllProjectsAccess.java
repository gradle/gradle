/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;

/**
 * Given out when taking the all projects lock to allow retrieving the mutable state of
 * {@link org.gradle.api.internal.project.ProjectState} without extra locking checks.
 * Instances must never live longer than the lambda, and calls on this interface will
 * fail after the lock has been released.
 */
public interface AllProjectsAccess {
    /**
     * Returns the mutable model for the given project.
     *
     * <p>
     * This differs from calling {@link ProjectState#getMutableModel()} because this method has additional checks to ensure that the caller still holds the all projects lock.
     * The other method does not perform any lock checks at all, and so may introduce race conditions if used directly.
     * </p>
     *
     * <p>
     * The return value and any mutable state derived from it that is not explicitly documented as thread-safe must not be returned from the lambda that was given this access.
     * </p>
     *
     * @param project the project to get the mutable model for
     * @return the mutable model for the given project
     */
    ProjectInternal getMutableModel(ProjectState project);
}
