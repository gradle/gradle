/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.RuleAction;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/***
 * Represents a container for component selection rules.  Rules can be applied as part of the
 * resolutionStrategy of a configuration and individual components can be explicitly accepted
 * or rejected by rule.  Components that are neither accepted or rejected will be subject to
 * the default version matching strategies.
 *
 * <pre>
 *     configurations {
 *         conf {
 *             resolutionStrategy {
 *                 componentSelection {
 *                     all { ComponentSelection selection ->
 *                         if (selection.candidate.name == 'someModule' && selection.candidate.version == '1.1') {
 *                             selection.reject("bad version '1.1' for 'someModule'")
 *                         }
 *                     }
 *                     all { ComponentSelection selection, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
 *                         if (selection.candidate.name == 'someModule' && descriptor.branch == 'testing') {
 *                             if (metadata.status != 'milestone') {
 *                                 selection.reject("only use milestones for someModule:testing")
 *                             }
 *                         }
 *                     }
 *                     module("org.sample:api") { ComponentSelection selection ->
 *                         if (selection.candidate.version == "1.1") {
 *                             selection.reject("known bad version")
 *                         }
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 */
@HasInternalProtocol
@Incubating
public interface ComponentSelectionRules {
    /**
     * Adds a simple component selection rule that will apply to all resolved components.
     * Each rule will receive a {@link ComponentSelection} object as an argument.
     *
     * @param selectionAction the Action that implements a rule to be applied
     * @return this
     */
    public ComponentSelectionRules all(Action<? super ComponentSelection> selectionAction);

    /**
     * Adds a component selection rule that will apply to all resolved components.
     *
     * Each rule will receive a {@link ComponentSelection} object as an argument
     * as well as any other inputs defined in the {@link org.gradle.api.RuleAction}. Allowable values for
     * {@link org.gradle.api.RuleAction#getInputTypes()} are {@link org.gradle.api.artifacts.ComponentMetadata} and {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.
     *
     * @param ruleAction the RuleAction that implements a rule to be applied
     * @return this
     */
    public ComponentSelectionRules all(RuleAction<? super ComponentSelection> ruleAction);

    /**
     * Adds a component selection rule that will apply to all resolved components.
     *
     * Each rule will receive a {@link ComponentSelection} object as an argument
     * as well as any other arguments specified for the closure.
     * Allowable closure arguments are {@link ComponentSelection} (required),
     * {@link org.gradle.api.artifacts.ComponentMetadata} and/or
     * {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.
     *
     * @param closure the Closure that implements a rule to be applied
     * @return this
     */
    public ComponentSelectionRules all(Closure<?> closure);

    /**
     * Adds a component selection rule that will apply to the specified module.
     * Each rule will receive a {@link ComponentSelection} object as an argument.
     *
     * @param id the module to apply this rule to in "group:module" format or as a {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param selectionAction the Action that implements a rule to be applied
     * @return this
     */
    public ComponentSelectionRules module(Object id, Action<? super ComponentSelection> selectionAction);

    /**
     * Adds a component selection rule that will apply to the specified module.
     *
     * Each rule will receive a {@link ComponentSelection} object as an argument
     * as well as any other inputs defined in the {@link org.gradle.api.RuleAction}. Allowable values for
     * {@link org.gradle.api.RuleAction#getInputTypes()} are {@link org.gradle.api.artifacts.ComponentMetadata} and {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.
     *
     * @param id the module to apply this rule to in "group:module" format or as a {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param ruleAction the RuleAction that implements a rule to be applied
     * @return this
     */
    public ComponentSelectionRules module(Object id, RuleAction<? super ComponentSelection> ruleAction);

    /**
     * Adds a component selection rule that will apply to the specified module.
     *
     * Each rule will receive a {@link ComponentSelection} object as an argument
     * as well as any other arguments specified for the closure.
     * Allowable closure arguments are {@link ComponentSelection} (required),
     * {@link org.gradle.api.artifacts.ComponentMetadata} and/or
     * {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.
     *
     * @param id the module to apply this rule to in "group:module" format or as a {@link org.gradle.api.artifacts.ModuleIdentifier}
     * @param closure the Closure that implements a rule to be applied
     * @return this
     */
    public ComponentSelectionRules module(Object id, Closure<?> closure);
}
