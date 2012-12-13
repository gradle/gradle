/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

/**
 * Provides details about a dependency when it is resolved.
 * Provides means to manipulate dependency metadata when it is resolved.
 *
 * @since 1.4
 */
@Incubating
public interface DependencyResolveDetails {

    /**
     * The module, before it is resolved.
     * The requested module does not change even if there are multiple dependency resolve actions
     * that manipulate the dependency metadata.
     */
    ModuleVersionSelector getRequested();

    /**
     * Allows to override the version when the dependency {@link #getRequested()} is resolved.
     * Can be used to select a version that is different than requested.
     * Forcing modules via {@link ResolutionStrategy#force(Object...)} uses this capability.
     * Configuring a version different than requested will cause {@link #getTarget()} method
     * return a target module with updated target version.
     *
     * It is valid to configure the same version as requested.
     *
     * @param version to use when resolving this dependency, cannot be null
     */
    void useVersion(String version);

    /**
     * The target module selector used to resolve the dependency.
     * Never returns null. Target module is updated when methods like {@link #useVersion(String)} are used.
     */
    ModuleVersionSelector getTarget();
}