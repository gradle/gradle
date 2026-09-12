/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * The predefined {@code Gradle} root problem group.
 * <p>
 * Initialization, configuration, and execution of the Gradle runtime, integration of built-in and third-party
 * plugins with Gradle.
 * <p>
 * This group is closed: it is not possible to create additional sub-groups below it or below its sub-groups.
 * Problems can be reported into any of its sub-groups.
 * <p>
 * Not intended for subclassing outside of Gradle. New members may be added in future versions.
 *
 * @see ProblemGroups
 * @since 9.9.0
 */
@Incubating
public abstract class GradleProblemGroup extends ProblemGroup {

    /**
     * Constructor.
     *
     * @since 9.9.0
     */
    protected GradleProblemGroup() {
    }

    /**
     * The {@code Build Cache} sub-group.
     * <p>
     * Caching of build outputs, including cache key calculation, local and remote cache storage, cache entry
     * retrieval, or cache configuration.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getBuildCache();

    /**
     * The {@code Build Definition} sub-group.
     * <p>
     * Configuration of settings and projects, including missing, invalid, or conflicting property values and
     * declarations.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getBuildDefinition();

    /**
     * The {@code Build Logic} sub-group.
     * <p>
     * Gradle plugin code interacting with the build model, including registration and configuration of tasks,
     * declaration of their inputs, outputs, cacheability, or calling other plugins' API.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getBuildLogic();

    /**
     * The {@code Configuration Cache} sub-group.
     * <p>
     * Caching of the configuration phase, including configuration inputs, serialization of the task graph, or
     * reports of task incompatibilities.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getConfigurationCache();

    /**
     * The {@code Deprecation} sub-group.
     * <p>
     * Usage of deprecated Gradle and plugins APIs, features, or behaviors scheduled for removal in a future version.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getDeprecation();

    /**
     * The {@code DSL Evaluation} sub-group.
     * <p>
     * Parsing, compilation, and application of Gradle DSL files.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getDslEvaluation();

    /**
     * The {@code Invocation} sub-group.
     * <p>
     * Build entry points such as CLI or Tooling API, including command-line options and arguments, environment
     * variables, requested tasks, or task options.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getInvocation();

    /**
     * The {@code Isolated Projects} sub-group.
     * <p>
     * Isolation of project configuration by forbidding cross-project access, including direct access or hierarchical
     * properties access.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getIsolatedProjects();

    /**
     * The {@code Plugin Validation} sub-group.
     * <p>
     * Validation of plugins, including incorrect use of caching or input annotations on tasks or artifact
     * transforms.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getPluginValidation();

    /**
     * The synthetic {@code Undefined} sub-group for problems without an explicitly defined {@code Gradle} sub-group.
     * <p>
     * Prefer one of the predefined sub-groups; this group exists so that a problem can be reported without inventing a category.
     *
     * @since 9.9.0
     */
    public abstract LeafProblemGroup getUndefined();
}
