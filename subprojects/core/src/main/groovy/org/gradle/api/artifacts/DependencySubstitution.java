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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.HasInternalProtocol;

/**
 * Provides means to substitute a different dependency during resolution.
 *
 * @since 2.5
 */
@Incubating
@HasInternalProtocol
public interface DependencySubstitution {
    /**
     * The requested dependency, before it is resolved.
     * The requested dependency does not change even if there are multiple dependency substitution rules
     * that manipulate the dependency metadata.
     */
    ComponentSelector getRequested();

    /**
     * This method can be used to replace a dependency before it is resolved,
     * e.g. change group, name or version (or all three of them), or replace it
     * with a project dependency.
     *
     * Accepted notations are:
     * <ul>
     *     <li>Strings encoding group:module:version, like 'org.gradle:gradle-core:2.4'</li>
     *     <li>Maps like [group: 'org.gradle', name: 'gradle-core', version: '2.4']</li>
     *     <li>Project instances like <code>project(":api")</code></li>
     *     <li>Any instance of <code>ModuleComponentSelector</code> or <code>ProjectComponentSelector</code></li>
     * </ul>
     *
     * @param notation the notation that gets parsed into an instance of {@link ComponentSelector}.
     */
    void useTarget(Object notation);
}
