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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.HasInternalProtocol;

/**
 * Allows replacing dependencies with other dependencies.
 *
 * @since 2.5
 */
@HasInternalProtocol
public interface DependencySubstitutions {
    /**
     * Adds a dependency substitution rule that is triggered for every dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link DependencySubstitution}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     * <p>
     * Example:
     * <pre class='autoTested'>
     * configurations { main }
     * // add dependency substitution rules
     * configurations.main.resolutionStrategy.dependencySubstitution {
     *   // Use a rule to change the dependency module while leaving group + version intact
     *   all { DependencySubstitution dependency -&gt;
     *     if (dependency.requested instanceof ModuleComponentSelector &amp;&amp; dependency.requested.module == 'groovy-all') {
     *       dependency.useTarget dependency.requested.group + ':groovy:' + dependency.requested.version
     *     }
     *   }
     *   // Use a rule to replace all missing projects with module dependencies
     *   all { DependencySubstitution dependency -&gt;
     *    if (dependency.requested instanceof ProjectComponentSelector) {
     *       def targetProject = findProject(":${dependency.requested.path}")
     *       if (targetProject == null) {
     *         dependency.useTarget "org.myorg:" + dependency.requested.path + ":+"
     *       }
     *     }
     *   }
     * }
     * </pre>
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
     * Transforms the supplied selector into a specific variant selector.
     *
     * @param selector the origin selector
     * @param detailsAction the variant selection details configuration
     * @since 6.6
     */
    ComponentSelector variant(ComponentSelector selector, Action<? super VariantSelectionDetails> detailsAction);

    /**
     * Transforms the provided selector into a platform selector.
     *
     * @param selector the original selector
     * @since 6.6
     */
    ComponentSelector platform(ComponentSelector selector);

    /**
     * DSL-friendly mechanism to construct a dependency substitution for dependencies matching the provided selector.
     * <p>
     * Examples:
     * <pre class='autoTested'>
     * configurations { main }
     * configurations.main.resolutionStrategy.dependencySubstitution {
     *   // Substitute project and module dependencies
     *   substitute module('org.gradle:api') using project(':api')
     *   substitute project(':util') using module('org.gradle:util:3.0')
     *
     *   // Substitute one module dependency for another
     *   substitute module('org.gradle:api:2.0') using module('org.gradle:api:2.1')
     * }
     * </pre>
     */
    Substitution substitute(ComponentSelector substitutedDependency);

    /**
     * Provides a DSL-friendly mechanism for specifying the target of a substitution.
     */
    interface Substitution {
        /**
         * Specify a reason for the substitution. This is optional
         *
         * @param reason the reason for the selection
         * @return the substitution
         * @since 4.5
         */
        Substitution because(String reason);

        /**
         * Specifies that the substituted target dependency should use the specified classifier.
         *
         * This method assumes that the target dependency is a jar (type jar, extension jar).
         *
         * @since 6.6
         */
        Substitution withClassifier(String classifier);

        /**
         * Specifies that the substituted dependency mustn't have any classifier.
         * It can be used whenever you need to substitute a dependency which uses a classifier into
         * a dependency which doesn't.
         *
         * This method assumes that the target dependency is a jar (type jar, extension jar).
         *
         * @since 6.6
         */
        Substitution withoutClassifier();

        /**
         * Specifies that substituted dependencies must not carry any artifact selector.
         *
         * @since 6.6
         */
        Substitution withoutArtifactSelectors();

        /**
         * Specify the target of the substitution.
         *
         * @deprecated Use {@link #using(ComponentSelector)} instead. This method will be removed in Gradle 8.0.
         */
        @Deprecated
        void with(ComponentSelector notation);


        /**
         * Specify the target of the substitution. This is a replacement for the {@link #with(ComponentSelector)}
         * method which supports chaining.
         *
         * @since 6.6
         */
        Substitution using(ComponentSelector notation);
    }
}
