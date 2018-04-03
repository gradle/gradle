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

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

import static groovy.lang.Closure.DELEGATE_FIRST;

/**
 * A {@code ModuleDependency} is a {@link org.gradle.api.artifacts.Dependency} on a module outside the current project.
 *
 * <p>
 * For examples on configuring the exclude rules please refer to {@link #exclude(java.util.Map)}.
 */
public interface ModuleDependency extends Dependency {
    /**
     * Adds an exclude rule to exclude transitive dependencies of this dependency.
     * <p>
     * Excluding a particular transitive dependency does not guarantee that it does not show up
     * in the dependencies of a given configuration.
     * For example, some other dependency, which does not have any exclude rules,
     * might pull in exactly the same transitive dependency.
     * To guarantee that the transitive dependency is excluded from the entire configuration
     * please use per-configuration exclude rules: {@link Configuration#getExcludeRules()}.
     * In fact, in majority of cases the actual intention of configuring per-dependency exclusions
     * is really excluding a dependency from the entire configuration (or classpath).
     * <p>
     * If your intention is to exclude a particular transitive dependency
     * because you don't like the version it pulls in to the configuration
     * then consider using forced versions' feature: {@link ResolutionStrategy#force(Object...)}.
     *
     * <pre class='autoTested'>
     * apply plugin: 'java' //so that I can declare 'compile' dependencies
     *
     * dependencies {
     *   compile('org.hibernate:hibernate:3.1') {
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
     *
     * @see #addArtifact(DependencyArtifact)
     */
    Set<DependencyArtifact> getArtifacts();

    /**
     * <p>Adds an artifact to this dependency.</p>
     *
     * <p>If no artifact is added to a dependency, an implicit default artifact is used. This default artifact has the
     * same name as the module and its type and extension is <em>jar</em>. If at least one artifact is explicitly added,
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
     * same name as the module and its type and extension is <em>jar</em>. If at least one artifact is explicitly added,
     * the implicit default artifact won't be used any longer.</p>
     *
     * @return this
     */
    DependencyArtifact artifact(@DelegatesTo(value = DependencyArtifact.class, strategy = DELEGATE_FIRST) Closure configureClosure);

    /**
     * <p>Adds an artifact to this dependency. The given action is passed a {@link
     * org.gradle.api.artifacts.DependencyArtifact} instance, which it can configure.</p>
     *
     * <p>If no artifact is added to a dependency, an implicit default artifact is used. This default artifact has the
     * same name as the module and its type and extension is <em>jar</em>. If at least one artifact is explicitly added,
     * the implicit default artifact won't be used any longer.</p>
     *
     * @return this
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
     * Returns the requested target configuration of this dependency. This is the name of the configuration in the target module that should be used when
     * selecting the matching configuration. If {@code null}, a default configuration should be used.
     */
    @Nullable
    String getTargetConfiguration();

    /**
     * Sets the requested target configuration of this dependency. This is the name of the configuration in the target module that should be used when
     * selecting the matching configuration. If {@code null}, a default configuration will be used.
     *
     * @since 4.0
     */
    @Incubating
    void setTargetConfiguration(@Nullable String name);

    /**
     * {@inheritDoc}
     */
    ModuleDependency copy();

}
