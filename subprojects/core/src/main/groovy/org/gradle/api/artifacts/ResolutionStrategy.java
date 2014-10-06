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
 * For example, forcing certain dependency versions, conflict resolutions or snapshot timeouts.
 * <p>
 * Examples:
 * <pre autoTested=''>
 * apply plugin: 'java' //so that there are some configurations
 *
 * configurations.all {
 *   resolutionStrategy {
 *     // fail eagerly on version conflict (includes transitive dependencies)
 *     // e.g. multiple different versions of the same dependency (group and name are equal)
 *     failOnVersionConflict()
 *
 *     // force certain versions of dependencies (including transitive)
 *     //  *append new forced modules:
 *     force 'asm:asm-all:3.3.1', 'commons-io:commons-io:1.4'
 *     //  *replace existing forced modules with new ones:
 *     forcedModules = ['asm:asm-all:3.3.1']
 *
 *     // add a dependency resolve rule
 *     eachDependency { DependencyResolveDetails details ->
 *       //specifying a fixed version for all libraries with 'org.gradle' group
 *       if (details.requested.group == 'org.gradle') {
 *           details.useVersion'1.4'
 *       }
 *       //changing 'groovy-all' into 'groovy':
 *       if (details.requested.name == 'groovy-all') {
 *          details.useTarget group: details.requested.group, name: 'groovy', version: details.requested.version
 *       }
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
     * <pre autoTested=''>
     * apply plugin: 'java' //so that there are some configurations
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
     * <pre autoTested=''>
     * apply plugin: 'java' //so that there are some configurations
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
     * <pre autoTested=''>
     * apply plugin: 'java' //so that there are some configurations
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
     * Adds a dependency resolve rule that is triggered for every dependency (including transitive)
     * when the configuration is being resolved. The action receives an instance of {@link DependencyResolveDetails}
     * that can be used to find out what dependency is being resolved and to influence the resolution process.
     * Example:
     * <pre autoTested=''>
     * apply plugin: 'java' //so that there are some configurations
     *
     * configurations.all {
     *   resolutionStrategy {
     *     eachDependency { DependencyResolveDetails details ->
     *       //specifying a fixed version for all libraries with 'org.gradle' group
     *       if (details.requested.group == 'org.gradle') {
     *         details.useVersion '1.4'
     *       }
     *     }
     *     eachDependency { details ->
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
    @Incubating
    ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule);

    /**
     * Sets the length of time that dynamic versions will be cached, with units expressed as a String.
     *
     * <p>A convenience method for {@link #cacheDynamicVersionsFor(int, java.util.concurrent.TimeUnit)} with units expressed as a String.
     * Units are resolved by calling the {@code valueOf(String)} method of {@link java.util.concurrent.TimeUnit} with the upper-cased string value.</p>
     * @param value The number of time units
     * @param units The units
     * @since 1.0-milestone-6
     */
    void cacheDynamicVersionsFor(int value, String units);

    /**
     * Sets the length of time that dynamic versions will be cached.
     *
     * <p>Gradle keeps a cache of dynamic version => resolved version (ie 2.+ => 2.3). By default, these cached values are kept for 24 hours, after which the cached entry is expired
     * and the dynamic version is resolved again.</p>
     * <p>Use this method to provide a custom expiry time after which the cached value for any dynamic version will be expired.</p>
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
    @Incubating
    ComponentSelectionRules getComponentSelection();

    /**
     * The componentSelection block provides rules to filter or blacklist certain components from appearing in the resolution result.
     *
     * @param action Action to be applied to the {@link ComponentSelectionRules}
     * @return this ResolutionStrategy instance
     * @since 2.2
     */
    @Incubating
    ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action);
}
