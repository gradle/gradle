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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.Set;

import static groovy.lang.Closure.DELEGATE_FIRST;

/**
 * A {@code Configuration} represents a group of artifacts and their dependencies.
 * Find more information about declaring dependencies to a configuration
 * or about managing configurations in docs for {@link ConfigurationContainer}
 * <p>
 * Configuration is an instance of a {@link FileCollection}
 * that contains all dependencies (see also {@link #getAllDependencies()}) but not artifacts.
 * If you want to refer to the artifacts declared in this configuration
 * please use {@link #getArtifacts()} or {@link #getAllArtifacts()}.
 * Read more about declaring artifacts in the configuration in docs for {@link org.gradle.api.artifacts.dsl.ArtifactHandler}
 *
 * Please see the <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html" target="_top">Declaring Dependencies</a> User Manual chapter for more information.
 */
@HasInternalProtocol
public interface Configuration extends FileCollection, HasConfigurableAttributes<Configuration> {

    /**
     * Returns the resolution strategy used by this configuration.
     * The resolution strategy provides extra details on how to resolve this configuration.
     * See docs for {@link ResolutionStrategy} for more info and examples.
     *
     * @return resolution strategy
     * @since 1.0-milestone-6
     */
    ResolutionStrategy getResolutionStrategy();

    /**
     * The resolution strategy provides extra details on how to resolve this configuration.
     * See docs for {@link ResolutionStrategy} for more info and examples.
     *
     * @param closure closure applied to the {@link ResolutionStrategy}
     * @return this configuration instance
     * @since 1.0-milestone-6
     */
    Configuration resolutionStrategy(@DelegatesTo(value = ResolutionStrategy.class, strategy = DELEGATE_FIRST) Closure closure);

    /**
     * The resolution strategy provides extra details on how to resolve this configuration.
     * See docs for {@link ResolutionStrategy} for more info and examples.
     *
     * @param action action applied to the {@link ResolutionStrategy}
     * @return this configuration instance
     * @since 3.1
     */
    Configuration resolutionStrategy(Action<? super ResolutionStrategy> action);

    /**
     * The states a configuration can be into. A configuration is only mutable as long as it is
     * in the unresolved state.
     */
    enum State { UNRESOLVED, RESOLVED, RESOLVED_WITH_FAILURES }

    /**
     * Returns the state of the configuration.
     *
     * @see org.gradle.api.artifacts.Configuration.State
     * @return The state of the configuration
     */
    State getState();

    /**
     * Returns the name of this configuration.
     *
     * @return The configuration name, never null.
     */
    String getName();

    /**
     * A {@link org.gradle.api.Namer} namer for configurations that returns {@link #getName()}.
     */
    class Namer implements org.gradle.api.Namer<Configuration> {
        @Override
        public String determineName(Configuration c) {
            return c.getName();
        }
    }

    /**
     * Returns true if this is a visible configuration. A visible configuration is usable outside the project it belongs
     * to. The default value is true.
     *
     * @return true if this is a visible configuration.
     */
    boolean isVisible();

    /**
     * Sets the visibility of this configuration. When visible is set to true, this configuration is visible outside
     * the project it belongs to. The default value is true.
     *
     * @param visible true if this is a visible configuration
     * @return this configuration
     */
    Configuration setVisible(boolean visible);

    /**
     * Returns the names of the configurations which this configuration extends from. The artifacts of the super
     * configurations are also available in this configuration.
     *
     * @return The super configurations. Returns an empty set when this configuration does not extend any others.
     */
    Set<Configuration> getExtendsFrom();

    /**
     * Sets the configurations which this configuration extends from.
     *
     * @param superConfigs The super configuration. Should not be null.
     * @return this configuration
     */
    Configuration setExtendsFrom(Iterable<Configuration> superConfigs);

    /**
     * Adds the given configurations to the set of configuration which this configuration extends from.
     *
     * @param superConfigs The super configurations.
     * @return this configuration
     */
    Configuration extendsFrom(Configuration... superConfigs);

    /**
     * Returns the transitivity of this configuration. A transitive configuration contains the transitive closure of its
     * direct dependencies, and all their dependencies. An intransitive configuration contains only the direct
     * dependencies. The default value is true.
     *
     * @return true if this is a transitive configuration, false otherwise.
     */
    boolean isTransitive();

    /**
     * Sets the transitivity of this configuration. When set to true, this configuration will contain the transitive
     * closure of its dependencies and their dependencies. The default value is true.
     *
     * @param t true if this is a transitive configuration.
     * @return this configuration
     */
    Configuration setTransitive(boolean t);

    /**
     * Returns the description for this configuration.
     *
     * @return the description. May be null.
     */
    @Nullable
    String getDescription();

    /**
     * Sets the description for this configuration.
     *
     * @param description the description. May be null
     * @return this configuration
     */
    Configuration setDescription(@Nullable String description);

    /**
     * Gets a ordered set including this configuration and all superconfigurations
     * recursively.
     * @return the list of all configurations
     */
    Set<Configuration> getHierarchy();

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * the resulting set of files.
     *
     * @return The files of this configuration.
     */
    Set<File> resolve();

    /**
     * Takes a closure which gets coerced into a {@link Spec}. Behaves otherwise in the same way as
     * {@link #files(org.gradle.api.specs.Spec)}.
     *
     * @param dependencySpecClosure The closure describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The files of a subset of dependencies of this configuration.
     */
    Set<File> files(Closure dependencySpecClosure);

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration.
     * But only the resulting set of files belonging to the subset of dependencies specified by the dependencySpec
     * is returned.
     *
     * @param dependencySpec The spec describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The files of a subset of dependencies of this configuration.
     */
    Set<File> files(Spec<? super Dependency> dependencySpec);

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration.
     * But only the resulting set of files belonging to the specified dependencies
     * is returned.
     *
     * @param dependencies The dependencies to be resolved
     * @return The files of a subset of dependencies of this configuration.
     */
    Set<File> files(Dependency... dependencies);

    /**
     * Resolves this configuration lazily. The resolve happens when the elements of the returned {@link FileCollection} get accessed the first time.
     * This locates and downloads the files which make up this configuration. Only the resulting set of files belonging to the subset
     * of dependencies specified by the dependencySpec is contained in the FileCollection.
     *
     * @param dependencySpec The spec describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The FileCollection with a subset of dependencies of this configuration.
     */
    FileCollection fileCollection(Spec<? super Dependency> dependencySpec);

    /**
     * Takes a closure which gets coerced into a {@link Spec}. Behaves otherwise in the same way as
     * {@link #fileCollection(org.gradle.api.specs.Spec)}.
     *
     * @param dependencySpecClosure The closure describing a filter applied to the all the dependencies of this configuration (including dependencies from extended configurations).
     * @return The FileCollection with a subset of dependencies of this configuration.
     */
    FileCollection fileCollection(Closure dependencySpecClosure);

    /**
     * Resolves this configuration lazily. The resolve happens when the elements of the returned {@link FileCollection} get accessed the first time.
     * This locates and downloads the files which make up this configuration. Only the resulting set of files belonging to specified
     * dependencies is contained in the FileCollection.
     *
     * @param dependencies The dependencies for which the FileCollection should contain the files.
     * @return The FileCollection with a subset of dependencies of this configuration.
     */
    FileCollection fileCollection(Dependency... dependencies);

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * a {@link ResolvedConfiguration} that may be used to determine information about the resolve (including errors).
     *
     * @return The ResolvedConfiguration object
     */
    ResolvedConfiguration getResolvedConfiguration();

    /**
     * Returns the name of the task that upload the artifacts of this configuration to repositories
     * declared by the user.
     *
     * @return The name of the associated upload task
     * @see org.gradle.api.tasks.Upload
     */
    String getUploadTaskName();

    /**
     * Returns a {@code TaskDependency} object containing all required dependencies to build the local dependencies
     * (e.g.<!-- --> project dependencies) belonging to this configuration or to one of its super configurations.
     *
     * @return a TaskDependency object
     */
    @Override
    TaskDependency getBuildDependencies();

    /**
     * Returns a TaskDependency object containing dependencies on all tasks with the specified name from project
     * dependencies related to this configuration or one of its super configurations.  These other projects may be
     * projects this configuration depends on or projects with a similarly named configuration that depend on this one
     * based on the useDependOn argument.
     *
     * @param useDependedOn if true, add tasks from project dependencies in this configuration, otherwise use projects
     *                      from configurations with the same name that depend on this one.
     * @param taskName name of task to depend on
     * @return the populated TaskDependency object
     */
    TaskDependency getTaskDependencyFromProjectDependency(boolean useDependedOn, final String taskName);

    /**
     * Gets the set of declared dependencies directly contained in this configuration
     * (ignoring superconfigurations).
     * <p>
     * This method does not resolve the configuration. Therefore, the return value does not include
     * transitive dependencies.
     *
     * @return the set of dependencies
     * @see #extendsFrom(Configuration...)
     */
    DependencySet getDependencies();

    /**
     * Gets the complete set of declared dependencies including those contributed by
     * superconfigurations.
     * <p>
     * This method does not resolve the configuration. Therefore, the return value does not include
     * transitive dependencies.
     *
     * @return the (read-only) set of dependencies
     * @see #extendsFrom(Configuration...)
     */
    DependencySet getAllDependencies();

    /**
     * Gets the set of dependency constraints directly contained in this configuration
     * (ignoring superconfigurations).
     *
     * @return the set of dependency constraints
     *
     * @since 4.6
     */
    DependencyConstraintSet getDependencyConstraints();

    /**
     * <p>Gets the complete set of dependency constraints including those contributed by
     * superconfigurations.</p>
     *
     * @return the (read-only) set of dependency constraints
     *
     * @since 4.6
     */
    DependencyConstraintSet getAllDependencyConstraints();

    /**
     * Returns the artifacts of this configuration excluding the artifacts of extended configurations.
     *
     * @return The set.
     */
    PublishArtifactSet getArtifacts();

    /**
     * Returns the artifacts of this configuration including the artifacts of extended configurations.
     *
     * @return The (read-only) set.
     */
    PublishArtifactSet getAllArtifacts();

    /**
     * Returns the exclude rules applied for resolving any dependency of this configuration.
     *
     * @see #exclude(java.util.Map)
     * @return The exclude rules
     */
    Set<ExcludeRule> getExcludeRules();

    /**
     * Adds an exclude rule to exclude transitive dependencies for all dependencies of this configuration.
     * You can also add exclude rules per-dependency. See {@link ModuleDependency#exclude(java.util.Map)}.
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     */
    Configuration exclude(Map<String, String> excludeProperties);

    /**
     * Execute the given action if the configuration has no defined dependencies when it first participates in
     * dependency resolution. A {@code Configuration} will participate in dependency resolution
     * when:
     * <ul>
     *     <li>The {@link Configuration} itself is resolved</li>
     *     <li>Another {@link Configuration} that extends this one is resolved</li>
     *     <li>Another {@link Configuration} that references this one as a project dependency is resolved</li>
     * </ul>
     *
     * This method is useful for specifying default dependencies for a configuration:
     * <pre class='autoTested'>
     * configurations { conf }
     * configurations['conf'].defaultDependencies { dependencies -&gt;
     *      dependencies.add(owner.project.dependencies.create("org.gradle:my-util:1.0"))
     * }
     * </pre>
     *
     * A {@code Configuration} is considered empty even if it extends another, non-empty {@code Configuration}.
     *
     * If multiple actions are supplied, each action will be executed until the set of dependencies is no longer empty.
     * Remaining actions will be ignored.
     *
     * @param action the action to execute when the configuration has no defined dependencies.
     * @return this
     */
    Configuration defaultDependencies(Action<? super DependencySet> action);

    /**
     * Execute the given action before the configuration first participates in
     * dependency resolution. A {@code Configuration} will participate in dependency resolution
     * when:
     * <ul>
     *     <li>The {@link Configuration} itself is resolved</li>
     *     <li>Another {@link Configuration} that extends this one is resolved</li>
     *     <li>Another {@link Configuration} that references this one as a project dependency is resolved</li>
     * </ul>
     *
     * This method is useful for mutating the dependencies for a configuration:
     * <pre class='autoTested'>
     * configurations { conf }
     * configurations['conf'].withDependencies { dependencies -&gt;
     *      dependencies.each { dependency -&gt;
     *          if (dependency.version == null) {
     *              dependency.version { require '1.0' }
     *          }
     *      }
     * }
     * </pre>
     *
     * Actions will be executed in the order provided.
     *
     * @since 4.4
     * @param action a dependency action to execute before the configuration is used.
     * @return this
     */
    Configuration withDependencies(Action<? super DependencySet> action);

    /**
     * Returns all the configurations belonging to the same configuration container as this
     * configuration (including this configuration).
     *
     * @return All of the configurations belong to the configuration container that this set belongs to.
     */
    Set<Configuration> getAll();

    /**
     * Returns the incoming dependencies of this configuration.
     *
     * @return The incoming dependencies of this configuration. Never {@code null}.
     */
    ResolvableDependencies getIncoming();

    /**
     * Returns the outgoing artifacts of this configuration.
     *
     * @return The outgoing artifacts of this configuration.
     * @since 3.4
     */
    ConfigurationPublications getOutgoing();

    /**
     * Configures the outgoing artifacts of this configuration.
     *
     * @param action The action to perform the configuration.
     * @since 3.4
     */
    void outgoing(Action<? super ConfigurationPublications> action);

    /**
     * Creates a copy of this configuration that only contains the dependencies directly in this configuration
     * (without contributions from superconfigurations).  The new configuration will be in the
     * UNRESOLVED state, but will retain all other attributes of this configuration except superconfigurations.
     * {@link #getHierarchy()} for the copy will not include any superconfigurations.
     * @return copy of this configuration
     */
    Configuration copy();

    /**
     * Creates a copy of this configuration that contains the dependencies directly in this configuration
     * and those derived from superconfigurations.  The new configuration will be in the
     * UNRESOLVED state, but will retain all other attributes of this configuration except superconfigurations.
     * {@link #getHierarchy()} for the copy will not include any superconfigurations.
     * @return copy of this configuration
     */
    Configuration copyRecursive();

    /**
     * Creates a copy of this configuration ignoring superconfigurations (see {@link #copy()} but filtering
     * the dependencies using the specified dependency spec.
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copy(Spec<? super Dependency> dependencySpec);

    /**
     * Creates a copy of this configuration with dependencies from superconfigurations (see {@link #copyRecursive()})
     * but filtering the dependencies using the dependencySpec.
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copyRecursive(Spec<? super Dependency> dependencySpec);

    /**
     * Takes a closure which gets coerced into a {@link Spec}. Behaves otherwise in the same way as {@link #copy(org.gradle.api.specs.Spec)}
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copy(Closure dependencySpec);

    /**
     * Takes a closure which gets coerced into a {@link Spec}. Behaves otherwise in the same way as {@link #copyRecursive(org.gradle.api.specs.Spec)}
     *
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copyRecursive(Closure dependencySpec);

    /**
     * Configures if a configuration can be consumed.
     *
     * @since 3.3
     */
    void setCanBeConsumed(boolean allowed);

    /**
     * Returns true if this configuration can be consumed from another project, or published. Defaults to true.
     * @return true if this configuration can be consumed or published.
     * @since 3.3
     */
    boolean isCanBeConsumed();

    /**
     * Configures if a configuration can be resolved.
     *
     * @since 3.3
     */
    void setCanBeResolved(boolean allowed);

    /**
     * Returns true if it is allowed to query or resolve this configuration. Defaults to true.
     * @return true if this configuration can be queried or resolved.
     * @since 3.3
     */
    boolean isCanBeResolved();

}
