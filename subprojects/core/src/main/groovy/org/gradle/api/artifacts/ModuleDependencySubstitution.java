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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.HasInternalProtocol;

/**
 * Provides means to substitute a different dependency in place of a module dependency.
 *
 * @since 2.4
 */
@Incubating
@HasInternalProtocol
public interface ModuleDependencySubstitution extends DependencySubstitution<ModuleComponentSelector> {
    /**
     * Allows to override the version when the dependency {@link #getRequested()} is resolved.
     * Can be used to select a version that is different than requested.
     * Forcing modules via {@link ResolutionStrategy#force(Object...)} uses this capability.
     * <p>
     * If you need to change not only the version but also group or name please use the {@link #useTarget(Object)} method.
     *
     * @param version to use when resolving this dependency, cannot be null.
     * It is valid to configure the same version as requested.
     */
    void useVersion(String version);
}
