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

import groovy.lang.Closure;
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
 *   //specifying a fixed version for all libraries with 'org.gradle' group
 *   eachModule { ModuleDependencySubstitution details ->
 *     if (details.requested.group == 'org.gradle') {
 *       details.useVersion '2.4'
 *     }
 *     //changing 'groovy-all' into 'groovy':
 *     if (details.requested.name == 'groovy-all') {
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
     * when the configuration is being resolved. The action receives an instance of {@link DependencySubstitution<ComponentSelector>}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    // TODO:PREZI Perhaps we should call this eachDependency(), as we do it for example in ResolutionRules?
    DependencySubstitutions all(Action<? super DependencySubstitution<? super ComponentSelector>> rule);

    /**
     * Adds a dependency substitution rule that is triggered for every dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link DependencySubstitution<ComponentSelector>}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions all(Closure<?> rule);

    /**
     * Adds a dependency substitution rule that is triggered for every module dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ModuleDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions eachModule(Action<? super ModuleDependencySubstitution> rule);

    /**
     * Adds a dependency substitution rule that is triggered for every module dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ModuleDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions eachModule(Closure<?> rule);

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
     * Adds a dependency substitution rule that is triggered for a given module dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ModuleDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions withModule(Object id, Closure<?> rule);

    /**
     * Adds a dependency substitution rule that is triggered for every project dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ProjectDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions eachProject(Action<? super ProjectDependencySubstitution> rule);

    /**
     * Adds a dependency substitution rule that is triggered for every project dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link ProjectDependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions eachProject(Closure<?> rule);

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
    DependencySubstitutions withProject(Object id, Closure<?> rule);
}
