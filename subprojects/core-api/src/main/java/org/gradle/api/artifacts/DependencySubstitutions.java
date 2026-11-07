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
 * @since 2.4
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
     *     if (dependency.requested instanceof ModuleComponentSelector &amp;&amp; dependency.requested.module == "groovy-all") {
     *       dependency.useTarget(withModule(dependency.requested, dependency.requested.group, "groovy", dependency.requested.version))
     *     }
     *   }
     *   // Use a rule to replace missing projects with module dependencies
     *   def modulesByProjectPath = [":api": "api", ":util:core": "util-core"]
     *   all { DependencySubstitution dependency -&gt;
     *     if (dependency.requested instanceof ProjectComponentSelector) {
     *       def module = modulesByProjectPath[dependency.requested.projectPath]
     *       if (module != null &amp;&amp; findProject(dependency.requested.projectPath) == null) {
     *         dependency.useTarget(withModule(dependency.requested, "org.myorg", module, "+"))
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link ResolutionStrategy#force(Object...)}
     *
     * @return this
     * @since 2.4
     */
    DependencySubstitutions all(Action<? super DependencySubstitution> rule);

    /**
     * Create a ModuleComponentSelector from the provided input string. Strings must be in the format "{group}:{module}:{version}".
     * @since 2.5
     */
    ComponentSelector module(String notation);

    /**
     * Create a ProjectComponentSelector from the provided input string. Strings must be in the format ":path".
     * @since 2.5
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
     * Creates a module component selector for the given coordinates that keeps the
     * attributes and capability selectors of the given component selector.
     * <p>
     * Use this in a substitution rule to change which module coordinates a dependency
     * resolves to. For example, a {@code platform(...)} or {@code testFixtures(...)}
     * dependency stays a platform or test fixtures dependency:
     * <pre>
     * configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
     *   all { DependencySubstitution dependency -&gt;
     *     if (dependency.requested instanceof ModuleComponentSelector &amp;&amp; dependency.requested.group == "org.legacy") {
     *       dependency.useTarget(withModule(dependency.requested, "org.renamed", dependency.requested.module, dependency.requested.version))
     *     }
     *   }
     * }
     * </pre>
     * Capability selectors that name a feature, such as those of {@code testFixtures(...)}, are
     * relative to the selected component and therefore follow the new module. Capability selectors
     * with explicit coordinates are kept as they are.
     * <p>
     * The version is interpreted as in dependency notation. {@code '1.0'} requires 1.0 and
     * {@code '1.0!!'} strictly requires it. Use {@link #withModule(ComponentSelector, String, String, Action)}
     * for other rich versions.
     *
     * @param selector the selector whose attributes and capability selectors are kept
     * @param group the group of the module to select
     * @param name the name of the module to select
     * @param version the version of the module to select
     *
     * @return a module component selector for the given coordinates
     *
     * @since 9.10.0
     */
    @Incubating
    ComponentSelector withModule(ComponentSelector selector, String group, String name, String version);

    /**
     * Creates a module component selector for the given module, with a rich version configured
     * by the given action, that keeps the attributes and capability selectors of the given
     * component selector.
     * <p>
     * See {@link #withModule(ComponentSelector, String, String, String)}.
     *
     * @param selector the selector whose attributes and capability selectors are kept
     * @param group the group of the module to select
     * @param name the name of the module to select
     * @param version configures the version of the module to select
     *
     * @return a module component selector for the given module and version
     *
     * @since 9.10.0
     */
    @Incubating
    ComponentSelector withModule(ComponentSelector selector, String group, String name, Action<? super MutableVersionConstraint> version);

    /**
     * Creates a project component selector for the given project path that keeps the
     * attributes and capability selectors of the given component selector.
     * <p>
     * Use this in a substitution rule to replace a dependency with a project. For example, a
     * {@code platform(...)} or {@code testFixtures(...)} dependency stays a platform or
     * test fixtures dependency. The path is interpreted as by {@link #project(String)}.
     * <p>
     * Capability selectors that name a feature, such as those of {@code testFixtures(...)}, are
     * relative to the selected component and therefore follow the project. Capability selectors
     * with explicit coordinates are kept as they are.
     *
     * @param selector the selector whose attributes and capability selectors are kept
     * @param path the path of the project to select
     *
     * @return a project component selector for the given path
     *
     * @since 9.10.0
     */
    @Incubating
    ComponentSelector withProject(ComponentSelector selector, String path);

    /**
     * DSL-friendly mechanism to construct a dependency substitution for dependencies matching the provided selector.
     * <p>
     * Examples:
     * <pre>
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
     * @since 2.5
     */
    Substitution substitute(ComponentSelector substitutedDependency);

    /**
     * Provides a DSL-friendly mechanism for specifying the target of a substitution.
     * @since 2.5
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
         * Specify the target of the substitution. This is a replacement for the prior {@code #with(ComponentSelector)}
         * method which supports chaining.
         *
         * @since 6.6
         */
        Substitution using(ComponentSelector notation);
    }
}
