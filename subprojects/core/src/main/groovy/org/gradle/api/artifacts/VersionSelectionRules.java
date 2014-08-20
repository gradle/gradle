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
 * Represents a container for version selection rules.  Rules can be applied as part of the
 * resolutionStrategy of a configuration and individual versions can be explicitly accepted
 * or rejected by rule.  Versions that are neither accepted or rejected will be subject to
 * the default version matching strategies.
 *
 * <pre>
 *     configurations {
 *         conf {
 *             resolutionStrategy {
 *                 versionSelection {
 *                     all { VersionSelection selection ->
 *                         if (selection.candidate.name == 'someModule' && selection.candidate.version == '1.1') {
 *                             selection.reject()
 *                         }
 *                     }
 *                     all { VersionSelection selection, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
 *                         if (selection.candidate.name == 'someModule'
 *                                  && descriptor.branch == 'testing'
 *                                  && metadata.status == 'milestone') {
 *                                      selection.accept()
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
public interface VersionSelectionRules {
    /**
     * Adds a simple version selection rule that will apply to all resolved components.
     * Each rule will receive a {@link org.gradle.api.artifacts.VersionSelection} object as an argument.
     *
     * @param selectionAction the Action or Closure that implements a rule to be applied
     * @return this
     */
    public VersionSelectionRules all(Action<? super VersionSelection> selectionAction);

    /**
     * Adds a version selection rule that will apply to all resolved components.
     *
     * Each rule will receive a {@link org.gradle.api.artifacts.VersionSelection} object as an argument
     * as well as any other inputs defined in the {@link org.gradle.api.RuleAction}. Allowable values for
     * {@link org.gradle.api.RuleAction#getInputTypes()} are {@link org.gradle.api.artifacts.ComponentMetadata} and {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.
     *
     * @param ruleAction the MetadataRule that implements a rule to be applied
     * @return this
     */
    public VersionSelectionRules all(RuleAction<? super VersionSelection> ruleAction);

    /**
     * Adds a version selection rule that will apply to all resolved components.
     *
     * Each rule will receive a {@link org.gradle.api.artifacts.VersionSelection} object as an argument
     * as well as any other arguments specified for the closure.
     * Allowable closure arguments are {@link org.gradle.api.artifacts.VersionSelection} (required),
     * {@link org.gradle.api.artifacts.ComponentMetadata} and/or
     * {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor}.
     *
     * @param closure the Closure that implements a rule to be applied
     * @return this
     */
    public VersionSelectionRules all(Closure<?> closure);
}
