/*
 * Copyright 2011 the original author or authors.
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

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Defines the strategies around dependency resolution.
 * For example, forcing certain dependency versions, substitutions, conflict resolutions or snapshot timeouts.
 * <p>
 * Examples:
 * <pre class='autoTested'>
 * plugins {
 *     id 'java' // so that there are some configurations
 * }
 *
 * configurations.all {
 *   resolutionStrategy {
 *     // fail eagerly on version conflict (includes transitive dependencies)
 *     // e.g. multiple different versions of the same dependency (group and name are equal)
 *     failOnVersionConflict()
 *
 *     // prefer modules that are part of this build (multi-project or composite build) over external modules
 *     preferProjectModules()
 *
 *     // force certain versions of dependencies (including transitive)
 *     //  *append new forced modules:
 *     force 'asm:asm-all:3.3.1', 'commons-io:commons-io:1.4'
 *     //  *replace existing forced modules with new ones:
 *     forcedModules = ['asm:asm-all:3.3.1']
 *
 *     // add dependency substitution rules
 *     dependencySubstitution {
 *       substitute module('org.gradle:api') with project(':api')
 *       substitute project(':util') with module('org.gradle:util:3.0')
 *     }
 *
 *     // cache dynamic versions for 10 minutes
 *     cacheDynamicVersionsFor 10*60, 'seconds'
 *     // don't cache changing modules at all
 *     cacheChangingModulesFor 0, 'seconds'
 *   }
 * }
 * </pre>
 *
 * @since 1.0-milestone-6
 */
public interface ResolutionStrategy {

    /**
     * In case of conflict, Gradle by default uses the newest of conflicting versions.
     * However, you can change this behavior. Use this method to configure the resolution to fail eagerly on any version conflict, e.g.
     * multiple different versions of the same dependency (group and name are equal) in the same {@link Configuration}.
     * The check includes both first level and transitive dependencies. See example below:
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'java' // so that there are some configurations
     * }
     *
     * configurations.all {
     *   resolutionStrategy.failOnVersionConflict()
     * }
     * </pre>
     *
     * @return this resolution strategy instance
     * @since 1.0-milestone-6
     */
    ResolutionStrategy failOnVersionConflict();

    /**
     * If this method is called, Gradle will make sure that no dynamic version was used in the resulting dependency graph.
     * In practice, it means that if the resolved dependency graph contains a module and that the versions participating
     * in the selection of that module contain at least one dynamic version, then resolution will fail if the resolution
     * result can change because of this version selector.
     *
     * This can be used in cases you want to make sure your build is reproducible, <i>without</i> relying on
     * dependency locking.
     *
     * @return this resolution strategy
     * @since 6.1
     */
    @Incubating
    ResolutionStrategy failOnDynamicVersions();

    /**
     * If this method is called, Gradle will make sure that no changing version participates in resolution.
     *
     * This can be used in cases you want to make sure your build is reproducible, <i>without</i> relying on
     * dependency locking.
     *
     * @return this resolution strategy
     * @since 6.1
     */
    @Incubating
    ResolutionStrategy failOnChangingVersions();

    /**
     * Configures Gradle to fail the build is the resolution result is expected to be unstable, that is to say that
     * it includes dynamic versions or changing versions and therefore the result may change depending
     * on when the build is executed.
     *
     * This method is equivalent to calling both {@link #failOnDynamicVersions()} and
     * {@link #failOnChangingVersions()}.
     *
     * @return this resolution strategy
     * @since 6.1
     */
    @Incubating
    ResolutionStrategy failOnNonReproducibleResolution();

    /**
     * Gradle can resolve conflicts purely by version number or prioritize project dependencies over binary.
     * The default is <b>by version number</b>.<p>
     * This applies to both first level and transitive dependencies. See example below:
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'java' // so that there are some configurations
     * }
     *
     * configurations.all {
     *   resolutionStrategy.preferProjectModules()
     * }
     * </pre>
     *
     * @since 3.2
     */
    void preferProjectModules();

    /**
     * Activates dependency locking support in Gradle.
     * Once turned on on a configuration, resolution result can be saved and then reused for subsequent builds.
     * This enables reproducible builds when using dynamic versions.
     *
     * @return this resolution strategy instance
     * @since 4.8
     */
    ResolutionStrategy activateDependencyLocking();

    /**
     * Deactivates dependency locking support in Gradle.
     *
     * @return this resolution strategy instance
     * @since 6.0
     */
    @Incubating
    ResolutionStrategy deactivateDependencyLocking();


    /**
     * Deactivates dependency verification for this configuration.
     * You should always be careful when disabling verification, and in particular avoid
     * disabling it for verification of plugins, because a plugin could use this to disable
     * verification itself.
     *
     * @since 6.2
     */
    @Incubating
    ResolutionStrategy disableDependencyVerification();

    /**
     * Enabled dependency verification for this configuration.
     *
     * @since 6.2
     */
    @Incubating
    ResolutionStrategy enableDependencyVerification();

    /**
     * Allows forcing certain versions of dependencies, including transitive dependencies.
     * <b>Appends</b> new forced modules to be considered when resolving dependencies.
     * <p>
     * It accepts following notations:
     * <ul>
     *   <li>String in a format of: 'group:name:version', for example: 'org.gradle:gradle-core:1.0'</li>
     *   <li>instance of {@link ModuleVersionSelector}</li>
     *   <li>any collection or array of above will be automatically flattened</li>
     * </ul>
     * Example:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java' // so that there are some configurations
     * }
     *
     * configurations.all {
     *   resolutionStrategy.force 'asm:asm-all:3.3.1', 'commons-io:commons-io:1.4'
     * }
     * </pre>
     *
     * @param moduleVersionSelectorNotations typically group:name:version notations to append
     * @return this ResolutionStrategy instance
     * @since 1.0-milestone-7
     */
    ResolutionStrategy force(Object... moduleVersionSelectorNotations);

    /**
     * Allows forcing certain versions of dependencies, including transitive dependencies.
     * <b>Replaces</b> existing forced modules with the input.
     * <p>
     * For information on notations see {@link #force(Object...)}
     * <p>
     * Example:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java' // so that there are some configurations
     * }
     *
     * configurations.all {
     *   resolutionStrategy.forcedModules = ['asm:asm-all:3.3.1', 'commons-io:commons-io:1.4']
     * }
     * </pre>
     *
     * @param moduleVersionSelectorNotations typically group:name:version notations to set
     * @return this ResolutionStrategy instance
     * @since 1.0-milestone-7
     */
    ResolutionStrategy setForcedModules(Object... moduleVersionSelectorNotations);

    /**
     * Returns currently configured forced modules. For more information on forcing versions see {@link #force(Object...)}
     *
     * @return forced modules
     * @since 1.0-milestone-7
     */
    Set<ModuleVersionSelector> getForcedModules();

    /**
     * Adds a dependency substitution rule that is triggered for every dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link DependencyResolveDetails}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     * Example:
     * <pre class='autoTested'>
     * configurations {
     *   compileClasspath.resolutionStrategy {
     *     eachDependency { DependencyResolveDetails details -&gt;
     *       //specifying a fixed version for all libraries with 'org.gradle' group
     *       if (details.requested.group == 'org.gradle') {
     *         details.useVersion '1.4'
     *       }
     *     }
     *     eachDependency { details -&gt;
     *       //multiple actions can be specified
     *       if (details.requested.name == 'groovy-all') {
     *          //changing the name:
     *          details.useTarget group: details.requested.group, name: 'groovy', version: details.requested.version
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * The rules are evaluated in order they are declared. Rules are evaluated after forced modules are applied (see {@link #force(Object...)}
     *
     * @return this
     * @since 1.4
     */
    ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule);

    /**
     * Sets the length of time that dynamic versions will be cached, with units expressed as a String.
     *
     * <p>A convenience method for {@link #cacheDynamicVersionsFor(int, java.util.concurrent.TimeUnit)} with units expressed as a String.
     * Units are resolved by calling the {@code valueOf(String)} method of {@link java.util.concurrent.TimeUnit} with the upper-cased string value.</p>
     *
     * @param value The number of time units
     * @param units The units
     * @since 1.0-milestone-6
     */
    void cacheDynamicVersionsFor(int value, String units);

    /**
     * Sets the length of time that dynamic versions will be cached.
     *
     * <p>Gradle keeps a cache of dynamic version =&gt; resolved version (ie 2.+ =&gt; 2.3). By default, these cached values are kept for 24 hours, after which the cached entry is expired
     * and the dynamic version is resolved again.</p>
     * <p>Use this method to provide a custom expiry time after which the cached value for any dynamic version will be expired.</p>
     *
     * @param value The number of time units
     * @param units The units
     * @since 1.0-milestone-6
     */
    void cacheDynamicVersionsFor(int value, TimeUnit units);

    /**
     * Sets the length of time that changing modules will be cached, with units expressed as a String.
     *
     * <p>A convenience method for {@link #cacheChangingModulesFor(int, java.util.concurrent.TimeUnit)} with units expressed as a String.
     * Units are resolved by calling the {@code valueOf(String)} method of {@link java.util.concurrent.TimeUnit} with the upper-cased string value.</p>
     *
     * @param value The number of time units
     * @param units The units
     * @since 1.0-milestone-6
     */
    void cacheChangingModulesFor(int value, String units);

    /**
     * Sets the length of time that changing modules will be cached.
     *
     * <p>Gradle caches the contents and artifacts of changing modules. By default, these cached values are kept for 24 hours,
     * after which the cached entry is expired and the module is resolved again.</p>
     * <p>Use this method to provide a custom expiry time after which the cached entries for any changing module will be expired.</p>
     *
     * @param value The number of time units
     * @param units The units
     * @since 1.0-milestone-6
     */
    void cacheChangingModulesFor(int value, TimeUnit units);

    /**
     * Returns the currently configured version selection rules object.
     *
     * @return the version selection rules
     * @since 2.2
     */
    ComponentSelectionRules getComponentSelection();

    /**
     * The componentSelection block provides rules to filter or prevent certain components from appearing in the resolution result.
     *
     * @param action Action to be applied to the {@link ComponentSelectionRules}
     * @return this ResolutionStrategy instance
     * @since 2.2
     */
    ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action);

    /**
     * Returns the set of dependency substitution rules that are set for this configuration.
     *
     * @since 2.5
     */
    DependencySubstitutions getDependencySubstitution();

    /**
     * Configures the set of dependency substitution rules for this configuration.  The action receives an instance of {@link DependencySubstitutions} which
     * can then be configured with substitution rules.
     * <p>Examples:</p>
     * <pre class='autoTested'>
     * // add dependency substitution rules
     * configurations.all {
     *   resolutionStrategy.dependencySubstitution {
     *     // Substitute project and module dependencies
     *     substitute module('org.gradle:api') with project(':api')
     *     substitute project(':util') with module('org.gradle:util:3.0')
     *
     *     // Substitute one module dependency for another
     *     substitute module('org.gradle:api:2.0') with module('org.gradle:api:2.1')
     *   }
     * }
     * </pre>
     *
     * @return this ResolutionStrategy instance
     * @see DependencySubstitutions
     * @since 2.5
     */
    ResolutionStrategy dependencySubstitution(Action<? super DependencySubstitutions> action);

    /**
     * Specifies the ordering for resolved artifacts. Options are:
     * <ul>
     * <li>{@link SortOrder#DEFAULT} : Don't specify the sort order. Gradle will provide artifacts in the default order.</li>
     * <li>{@link SortOrder#CONSUMER_FIRST} : Artifacts for a consuming component should appear <em>before</em> artifacts for its dependencies.</li>
     * <li>{@link SortOrder#DEPENDENCY_FIRST} : Artifacts for a consuming component should appear <em>after</em> artifacts for its dependencies.</li>
     * </ul>
     * A best attempt will be made to sort artifacts according the supplied {@link SortOrder}, but no guarantees will be made in the presence of dependency cycles.
     *
     * NOTE: For a particular Gradle version, artifact ordering will be consistent. Multiple resolves for the same inputs will result in the
     * same outputs in the same order.
     *
     * @since 3.5
     */
    void sortArtifacts(SortOrder sortOrder);

    /**
     * Configures the capabilities resolution strategy.
     *
     * @param action the configuration action.
     * @return this resolution strategy
     * @since 5.6
     */
    @Incubating
    ResolutionStrategy capabilitiesResolution(Action<? super CapabilitiesResolution> action);

    /**
     * Returns the capabilities resolution strategy.
     *
     * @since 5.6
     */
    @Incubating
    CapabilitiesResolution getCapabilitiesResolution();

    /**
     * Defines the sort order for components and artifacts produced by the configuration.
     *
     * @see #sortArtifacts(SortOrder)
     * @since 3.5
     */
    enum SortOrder {
        DEFAULT, CONSUMER_FIRST, DEPENDENCY_FIRST
    }
}
