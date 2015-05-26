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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.HasInternalProtocol;

/**
 * Allows replacing dependencies with other dependencies.
 *
 * <pre>
 * // add dependency substitution rules
 * dependencySubstitution {
 *   // Substitute project and module dependencies
 *   substitute module('org.gradle:api') with project(':api')
 *   substitute project(':util') with module('org.gradle:util:3.0')
 *
 *   // Substitute one module dependency for another
 *   substitute module('org.gradle:api:2.0') with module('org.gradle:api:2.1')
 *
 *   // Use a rule to change the dependency module while leaving group + version intact
 *   all { DependencySubstitution details ->
 *     if (details.requested instanceof ModuleComponentSelector && details.requested.name == 'groovy-all') {
 *       details.useTarget details.requested.group + ':groovy:' + details.requested.version
 *     }
 *   }
 * }
 * </pre>
 * @since 2.5
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
     */
    DependencySubstitutions all(Action<? super DependencySubstitution> rule);

    /**
     * Create a ModuleComponentSelector from the provided input string. Strings must be in the format "{group}:{module}:{version}".
     */
    ComponentSelector module(String notation);

    /**
     * Create a ProjectComponentSelector from the provided input string. Strings must be in the format ":path".
     */
    ComponentSelector project(String path);

    /**
     * Construct a DSL-friendly substitution that will substitute dependencies matching the provided selector.
     */
    Substitution substitute(ComponentSelector substitutedDependency);

    /**
     * Provides a DSL-friendly mechanism for specifying the target of a substitution.
     */
    interface Substitution {
        /**
         * Specify the target of the substitution.
         */
        void with(ComponentSelector notation);
    }
}
