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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Allows replacing dependencies with other dependencies.
 *
 * <pre>
 * // add dependency substitution rules
 * dependencySubstitution {
 *   all { DependencySubstitution details ->
 *     // Use a local project dependency for any external dependency on 'org.gradle:util'
 *     if (details.requested instanceof ModuleComponentSelector
 *             && details.requested.group == 'org.gradle'
 *             && details.requested.name == 'util') {
 *       details.useTarget(project(':util'))
 *     }
 *
 *     //changing 'groovy-all' into 'groovy':
 *     if (details.requested instanceof ModuleComponentSelector
 *             && details.requested.name == 'groovy-all') {
 *       details.useTarget group: details.requested.group, name: 'groovy', version: details.requested.version
 *     }
 *   }
 * }
 * </pre>
 */
@HasInternalProtocol
@Incubating
public interface DependencySubstitutions {
    /**
     * Adds a dependency substitution rule that is triggered for every dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link DependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions all(Action<? super DependencySubstitution> rule);

    /**
     * Adds a dependency substitution rule that is triggered for a given module dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ModuleDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions withModule(Object id, Action<? super ModuleDependencySubstitution> rule);

    /**
     * Adds a dependency substitution rule that is triggered for a given project dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ProjectDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions withProject(Object id, Action<? super ProjectDependencySubstitution> rule);
}
