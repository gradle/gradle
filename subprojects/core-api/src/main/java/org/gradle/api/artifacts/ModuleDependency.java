/*
 * Copyright 2010 the original author or authors.
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
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.capabilities.Capability;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static groovy.lang.Closure.DELEGATE_FIRST;

/**
 * A {@code ModuleDependency} is a {@link org.gradle.api.artifacts.Dependency} on a component that exists
 * outside of the current project.
 * <p>
 * Modules can supply {@link ModuleDependency#getArtifacts() multiple artifacts} in addition to the
 * {@link #addArtifact(DependencyArtifact) implicit default artifact}.  Non-default artifacts
 * available in a module can be selected by a consumer by specifying a classifier or extension
 * when declaring a dependency on that module.
 * <p>
 * For examples on configuring exclude rules for modules please refer to {@link #exclude(java.util.Map)}.
 */
public interface ModuleDependency extends Dependency, HasConfigurableAttributes<ModuleDependency> {
    /**
     * Adds an exclude rule to exclude transitive dependencies of this dependency.
     * <p>
     * Excluding a particular transitive dependency does not guarantee that it does not show up
     * in the dependencies of a given configuration.
     * For example, some other dependency, which does not have any exclude rules,
     * might pull in exactly the same transitive dependency.
     * To guarantee that the transitive dependency is excluded from the entire configuration
     * please use per-configuration exclude rules: {@link Configuration#getExcludeRules()}.
     * In fact, in a majority of cases the actual intention of configuring per-dependency exclusions
     * is really excluding a dependency from the entire configuration (or classpath).
     * <p>
     * If your intention is to exclude a particular transitive dependency
     * because you don't like the version it pulls in to the configuration
     * then consider using forced versions' feature: {@link ResolutionStrategy#force(Object...)}.
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'java' // so that I can declare 'implementation' dependencies
     * }
     *
     * dependencies {
     *   implementation('org.hibernate:hibernate:3.1') {
     *     //excluding a particular transitive dependency:
     *     exclude module: 'cglib' //by artifact name
     *     exclude group: 'org.jmock' //by group
     *     exclude group: 'org.unwanted', module: 'iAmBuggy' //by both name and group
     *   }
     * }
     * </pre>
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     */
    ModuleDependency exclude(Map<String, String> excludeProperties);

    /**
     * Returns the exclude rules for this dependency.
     *
     * @see #exclude(java.util.Map)
     */
    Set<ExcludeRule> getExcludeRules();

    /**
     * Returns the artifacts belonging to this dependency.
     * <p>
     * Initially, a dependency has no artifacts, so this can return an empty set.  Typically, however, a producer
     * project will add a single artifact to a module, which will be represented in this collection via a
     * single element.  But this is <strong>NOT</strong> always true.  Modules can use
     * custom classifiers or extensions to distinguish multiple artifacts that they contain.
     * <p>
     * In general, projects publishing using Gradle should favor supplying multiple artifacts by supplying
     * multiple variants, each containing a different artifact, that are selectable through variant-aware
     * dependency resolution.  This mechanism where a module contains multiple artifacts is primarily
     * intended to support dependencies on non-Gradle-published components.
     *
     * @see #addArtifact(DependencyArtifact)
     */
    Set<DependencyArtifact> getArtifacts();

    /**
     * <p>Adds an artifact to this dependency.</p>
     *
     * <p>If no artifact is added to a dependency, an implicit default artifact is used. This default artifact has the
     * same name as the module and its type and extension is {@code jar}. If at least one artifact is explicitly added,
     * the implicit default artifact won't be used any longer.</p>
     *
     * @return this
     */
    ModuleDependency addArtifact(DependencyArtifact artifact);

    /**
     * <p>Adds an artifact to this dependency. The given closure is passed a {@link
     * org.gradle.api.artifacts.DependencyArtifact} instance, which it can configure.</p>
     *
     * <p>If no artifact is added to a dependency, an implicit default artifact is used. This default artifact has the
     * same name as the module and its type and extension is {@code jar}. If at least one artifact is explicitly added,
     * the implicit default artifact won't be used any longer.</p>
     *
     * @return the added artifact
     *
     * @see DependencyArtifact
     */
    DependencyArtifact artifact(@DelegatesTo(value = DependencyArtifact.class, strategy = DELEGATE_FIRST) Closure configureClosure);

    /**
     * <p>Adds an artifact to this dependency. The given action is passed a {@link
     * org.gradle.api.artifacts.DependencyArtifact} instance, which it can configure.</p>
     *
     * <p>If no artifact is added to a dependency, an implicit default artifact is used. This default artifact has the
     * same name as the module and its type and extension is {@code jar}. If at least one artifact is explicitly added,
     * the implicit default artifact won't be used any longer.</p>
     *
     * @return the added artifact
     *
     * @see DependencyArtifact
     *
     * @since 3.1
     */
    DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction);

    /**
     * Returns whether this dependency should be resolved including or excluding its transitive dependencies.
     *
     * @see #setTransitive(boolean)
     */
    boolean isTransitive();

    /**
     * Sets whether this dependency should be resolved including or excluding its transitive dependencies. The artifacts
     * belonging to this dependency might themselves have dependencies on other artifacts. The latter are called
     * transitive dependencies.
     *
     * @param transitive Whether transitive dependencies should be resolved.
     * @return this
     */
    ModuleDependency setTransitive(boolean transitive);

    /**
     * Returns the requested target configuration of this dependency.
     * <p>
     * If non-null, this overrides variant-aware dependency resolution and selects the
     * variant in the target component matching the requested configuration name.
     */
    @Nullable
    String getTargetConfiguration();

    /**
     * Sets the requested target configuration of this dependency.
     * <p>
     * This overrides variant-aware dependency resolution and selects the variant in the
     * target component matching the requested configuration name.
     * <p>
     * Using this method is <strong>discouraged</strong> except for selecting
     * configurations from Ivy components.
     *
     * @since 4.0
     */
    void setTargetConfiguration(@Nullable String name);

    /**
     * {@inheritDoc}
     */
    @Override
    ModuleDependency copy();

    /**
     * Returns the attributes for this dependency. Mutation of the attributes of a dependency must be done through
     * the {@link #attributes(Action)} method.
     *
     * @return the attributes container for this dependency
     *
     * @since 4.8
     */
    @Override
    AttributeContainer getAttributes();

    /**
     * Mutates the attributes of this dependency. Attributes are used during dependency resolution to select the appropriate
     * target variant, in particular when a single component provides different variants.
     *
     * @param configureAction the attributes mutation action
     * @return this
     *
     * @since 4.8
     */
    @Override
    ModuleDependency attributes(Action<? super AttributeContainer> configureAction);

    /**
     * Configures the requested capabilities of this dependency.
     *
     * @param configureAction the configuration action
     * @return this
     *
     * @since 5.3
     */
    ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction);

    /**
     * Returns the explicitly requested capabilities for this dependency.
     * <p>
     * Prefer {@link #getCapabilitySelectors()}. This method is not Isolated Projects compatible.
     *
     * @return An immutable view of all explicitly requested capabilities. Updates must be done calling {@link #capabilities(Action)}.
     *
     * @since 5.3
     */
    List<Capability> getRequestedCapabilities();

    /**
     * Returns the set of capabilities that are requested for this dependency
     *
     * @return A view of all requested capabilities. Updates must be done calling {@link #capabilities(Action)}.
     *
     * @since 8.11
     */
    @Incubating
    Set<CapabilitySelector> getCapabilitySelectors();

    /**
     * Endorse version constraints with {@link VersionConstraint#getStrictVersion()} strict versions} from the target module.
     *
     * Endorsing strict versions of another module/platform means that all strict versions will be interpreted during dependency
     * resolution as if they were defined by the endorsing module itself.
     *
     * @since 6.0
     */
    void endorseStrictVersions();

    /**
     * Resets the {@link #isEndorsingStrictVersions()} state of this dependency.
     *
     * @since 6.0
     */
    void doNotEndorseStrictVersions();

    /**
     * Are the {@link VersionConstraint#getStrictVersion()} strict version} dependency constraints of the target module endorsed?
     *
     * @since 6.0
     */
    boolean isEndorsingStrictVersions();
}
