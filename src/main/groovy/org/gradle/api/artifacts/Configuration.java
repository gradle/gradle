/*
 * Copyright 2008 the original author or authors.
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

import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * <p>A {@code Configuration} represents a group of artifacts and their dependencies.</p>
 */
public interface Configuration extends FileCollection {
    /**
     * The states a configuration can be into. A configuration is only mutable as long as it is
     * in the unresolved state.
     */
    enum State { UNRESOLVED, RESOLVED, RESOLVED_WITH_FAILURES }

    /**
     * Returns the state of the configuration.
     *
     * @see org.gradle.api.artifacts.Configuration.State
     */
    State getState();

    /**
     * Returns the name of this configuration.
     *
     * @return The configuration name, never null.
     */
    String getName();

    /**
     * Returns true if this is a visible configuration. A visible configuration is usable outside the project it belongs
     * to. The default value is true.
     *
     * @return true if this is a visible configuration.
     */
    boolean isVisible();

    /**
     * Sets the visibility of this configuration. When visible is set to true, this configuration is visibile outside
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
    Configuration setExtendsFrom(Set<Configuration> superConfigs);

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
    String getDescription();

    /**
     * Sets the description for this configuration.
     *
     * @param description the description. May be null
     * @return this configuration
     */
    Configuration setDescription(String description);

    /**
     * Gets a list including this configuration and all superconfigurations
     * recursively.
     * @return the list of all configurations
     */
    List<Configuration> getHierarchy();

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * the resulting list of files.
     *
     * @return The files of this configuration.
     */
    Set<File> resolve();

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * a ResolveReport that may be used to determine information about the resolve (including errors).
     *
     * @return The ResolveReport
     */
    ResolveReport resolveAsReport();

    /**
     * Returns the name of the task that upload the artifacts of this configuration to the internal
     * Gradle repository used resolving inter-project dependencies.
     */
    String getUploadInternalTaskName();

    /**
     * Returns the name of the task that upload the artifacts of this configuration to repositories
     * declared by the user.
     *
     * @see org.gradle.api.tasks.Upload
     */
    String getUploadTaskName();

    /**
     * Returns a task dependencies object containing all required dependencies to build the internal dependencies
     * (e.g. project dependencies) belonging to this configuration or to one of its super configurations.
     *
     * @return a task dependency object
     */
    TaskDependency getBuildDependencies();

    /**
     * Returns a task dependencies object containing all required dependencies to build the artifacts
     * belonging to this configuration or to one of its super configurations.
     *
     * @return a task dependency object
     */
    TaskDependency getBuildArtifacts();

    /**
     * Publishes the artifacts of this configuration to the specified repositories. This
     * method is usually used only internally as the users use the associated upload tasks to
     * upload the artifacts.
     * 
     * @param publishRepositories The repositories to publish the artifacts to.
     * @param publishInstruction Instructions for details of the upload.
     *
     * @see org.gradle.api.tasks.Upload
     * @see #getUploadTaskName() 
     */
    void publish(List<DependencyResolver> publishRepositories, PublishInstruction publishInstruction);

    /**
     * Gets the set of dependencies directly contained in this configuration
     * (ignoring superconfigurations).
     *
     * @return the set of dependencies
     */
    Set<Dependency> getDependencies();

    /**
     * Gets the complete set of dependencies including those contributed by
     * superconfigurations.
     *
     * @return the set of dependencies
     */
    Set<Dependency> getAllDependencies();

    /**
     * Gets the set of Project Dependencies directly contained in this configuration
     * (ignoring superconfigurations).
     *
     * @return the set of Project Dependencies
     */
    Set<ProjectDependency> getProjectDependencies();

    /**
     * Gets the complete set of Project Dependencies including those contributed by
     * superconfigurations.
     *
     * @return the set of Project Dependencies
     */
    Set<ProjectDependency> getAllProjectDependencies();

    /**
     * Adds a dependency to this configuration
     * 
     * @param dependency The dependency to be added.
     */
    void addDependency(Dependency dependency);

    /**
     * Returns the artifacts of this configuration excluding the artifacts of extended configurations.
     */
    Set<PublishArtifact> getArtifacts();

    /**
     * Returns the artifacts of this configuration including the artifacts of extended configurations.
     */
    Set<PublishArtifact> getAllArtifacts();

    /**
     * Returns the repositories used for resolving the dependencies of the configuration.
     */
    List<DependencyResolver> getDependencyResolvers();

    /**
     * Returns the exclude rules applied for resolving any dependency of this configuration.
     *
     * @see #exclude(java.util.Map) 
     */
    Set<ExcludeRule> getExcludeRules();

    /**
     * Adds an exclude rule to exclude transitive dependencies for all dependencies of this configuration.
     * You can also add exclude rules per-dependency. See {@link Dependency#exclude(java.util.Map)}. 
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     */
    Configuration exclude(Map<String, String> excludeProperties);

    /**
     * Returns all the configurations belonging to the same configuration container as this
     * configuration (including this configuration).
     */
    Set<Configuration> getAll();

    /**
     * Adds an artifact to be published to this configuration.
     * 
     * @param artifact The artifact.
     * @return this
     */
    Configuration addArtifact(PublishArtifact artifact);

    /**
     * Creates a copy of this configuration that only contains the dependencies directly in this configuration
     * (without contributions from superconfigurations).  The new configuation will be in the
     * UNRESOLVED state, but will retain all other attributes of this configuration except superconfigurations.
     * {@link #getHierarchy()} for the copy will not include any superconfigurations.
     * @return copy of this configuration
     */
    Configuration copy();

    /**
     * Creates a copy of this configuration that contains the dependencies directly in this configuration
     * and those derived from superconfigurations.  The new configuation will be in the
     * UNRESOLVED state, but will retain all other attributes of this configuration except superconfigurations.
     * {@link #getHierarchy()} for the copy will not include any superconfigurations.
     * @return copy of this configuration
     */
    Configuration copyRecursive();

    /**
     * Creates a copy of this configuration ignoring superconfigurations (see {@link #copy()} but filtering
     * the dependencies using the dependencySpec.  The dependencySpec may be obtained from
     * {@link org.gradle.api.artifacts.specs.DependencySpecs DependencySpecs.type()} like
     * DependencySpecs.type(Type.EXTERNAL)
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copy(Spec<Dependency> dependencySpec);

    /**
     * Creates a copy of this configuration with dependencies from superconfigurations (see {@link #copyRecursive()})
     *  but filtering the dependencies using the dependencySpec.  The dependencySpec may be obtained from
     * {@link org.gradle.api.artifacts.specs.DependencySpecs DependencySpecs.type()} like
     * DependencySpecs.type(Type.EXTERNAL)
     * @param dependencySpec filtering requirements
     * @return copy of this configuration
     */
    Configuration copyRecursive(Spec<Dependency> dependencySpec);
}
