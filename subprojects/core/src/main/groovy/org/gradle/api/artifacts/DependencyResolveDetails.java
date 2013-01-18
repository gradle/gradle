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
     * The requested module does not change even if there are multiple dependency resolve rules
     * that manipulate the dependency metadata.
     */
    ModuleVersionSelector getRequested();

    /**
     * Allows to override the version when the dependency {@link #getRequested()} is resolved.
     * Can be used to select a version that is different than requested.
     * Forcing modules via {@link ResolutionStrategy#force(Object...)} uses this capability.
     * Configuring a version different than requested will cause {@link #getTarget()} method
     * return a target module with updated target version.
     * <p>
     * If you need to change not only the version but also group or name please use the {@link #useTarget(Object)} method.
     *
     * @param version to use when resolving this dependency, cannot be null.
     * It is valid to configure the same version as requested.
     */
    void useVersion(String version);

    /**
     * Allows to override the details of the dependency (see {@link #getTarget()})
     * when it is resolved (see {@link #getRequested()}).
     * This method can be used to change the dependency before it is resolved,
     * e.g. change group, name or version (or all three of them).
     * In many cases users are interested in changing the version.
     * For such scenario you can use the {@link #useVersion(String)} method.
     *
     * @param notation the notation that gets parsed into an instance of {@link ModuleVersionSelector}.
     * You can pass Strings like 'org.gradle:gradle-core:1.4',
     * Maps like [group: 'org.gradle', name: 'gradle-core', version: '1.4'],
     * or instances of ModuleVersionSelector.
     *
     * @since 1.5
     */
    void useTarget(Object notation);

    /**
     * The target module selector used to resolve the dependency.
     * Never returns null. Target module is updated when methods like {@link #useVersion(String)} are used.
     */
    ModuleVersionSelector getTarget();
}