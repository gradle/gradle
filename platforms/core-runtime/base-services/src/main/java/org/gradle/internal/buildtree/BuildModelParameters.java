/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@ServiceScope(Scope.BuildTree.class)
public interface BuildModelParameters {

    /**
     * Whether project-scoped work should use project-lock or build-lock to synchronize,
     * allowing work from different projects to run in parallel when set to true.
     * <p>
     * Most notably, this allows parallel execution of tasks from different projects.
     * <p>
     * Note that this does not synchronize work from different builds.
     * <ul>
     * <li>Vintage: controlled by {@code --parallel} (or its property)
     * <li>CC: controlled by {@code --parallel} (or its property)
     * <li>IP: always enabled
     * </ul>
     */
    boolean isParallelProjectExecution();

    boolean isConfigureOnDemand();

    boolean isConfigurationCache();

    /**
     * User-friendly reason explaining why CC is disabled based on requested build options.
     * <p>
     * A common reason is the request for Gradle features incompatible with CC, such as {@code --export-keys}.
     */
    @Nullable
    String getConfigurationCacheDisabledReason();

    boolean isConfigurationCacheParallelStore();

    boolean isConfigurationCacheParallelLoad();

    boolean isIsolatedProjects();

    /**
     * Whether projects should be configured in parallel.
     * <p>
     * This should only take effect if {@link #isConfigureOnDemand() configure-on-demand}
     * is not making us skip eager project configuration.
     */
    boolean isParallelProjectConfiguration();

    /**
     * When {@link  #isIsolatedProjects()} is true, should project state be invalidated when a project it is coupled with changes?
     * This parameter is only used for benchmarking purposes.
     */
    boolean isInvalidateCoupledProjects();

    /**
     * Should model dependencies between projects be treated as project dependencies with respect to invalidation?
     * <p>
     * This parameter is only used for benchmarking purposes.
     */
    boolean isModelAsProjectDependency();

    /**
     * True when the build action requires to build Tooling Models.
     * <p>
     * When true, Gradle's "build model" such Gradle and Project state cannot be discarded
     * even after the tasks have been executed, because the Tooling Model Builders can run after tasks.
     */
    boolean isModelBuilding();

    /**
     * Determines whether nested build actions provided in {@code BuildController.run(actions)} can run in parallel.
     * <ul>
     * <li>Vintage: controlled by {@code --parallel}
     * <li>CC: not applicable, since CC is always disabled for model building invocations
     * <li>IP: always enabled
     * </ul>
     */
    boolean isParallelModelBuilding();

    /**
     * Determines whether models produced by tooling model builders are individually cached.
     * <p>
     * With IP, we can assume that for project-scoped models, their effective inputs are a subset of the Project state.
     * If the Project is up to date, we can serve its models from a cache instead of recomputing them on subsequent runs.
     * <p>
     * Always false for Vintage and CC.
     */
    boolean isCachingModelBuilding();

    /**
     * Returns true if the model building is resilient so some failures in model building.
     *
     * @return true if the model building is resilient, false otherwise
     */
    boolean isResilientModelBuilding();

    /**
     * Collects all properties and their values in a map for logging and testing purposes.
     */
    Map<String, ? extends @Nullable Object> toDisplayMap();
}
