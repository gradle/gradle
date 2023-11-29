/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class BuildModelParameters {
    private final boolean parallelProjectExecution;
    private final boolean configureOnDemand;
    private final boolean configurationCache;
    private final boolean isolatedProjects;
    private final boolean requiresBuildModel;
    private final boolean intermediateModelCache;
    private final boolean parallelToolingApiActions;
    private final boolean invalidateCoupledProjects;
    private final LogLevel configurationCacheLogLevel;

    public BuildModelParameters(
        boolean parallelProjectExecution,
        boolean configureOnDemand,
        boolean configurationCache,
        boolean isolatedProjects,
        boolean requiresBuildModel,
        boolean intermediateModelCache,
        boolean parallelToolingApiActions,
        boolean invalidateCoupledProjects,
        LogLevel configurationCacheLogLevel
    ) {
        this.parallelProjectExecution = parallelProjectExecution;
        this.configureOnDemand = configureOnDemand;
        this.configurationCache = configurationCache;
        this.isolatedProjects = isolatedProjects;
        this.requiresBuildModel = requiresBuildModel;
        this.intermediateModelCache = intermediateModelCache;
        this.parallelToolingApiActions = parallelToolingApiActions;
        this.invalidateCoupledProjects = invalidateCoupledProjects;
        this.configurationCacheLogLevel = configurationCacheLogLevel;
    }

    public boolean isParallelProjectExecution() {
        return parallelProjectExecution;
    }

    /**
     * Will the build model, that is the configured Gradle and Project objects, be required during the build execution?
     *
     * <p>When the build model is not required, certain state can be discarded or not created.
     */
    public boolean isRequiresBuildModel() {
        return requiresBuildModel;
    }

    public boolean isConfigureOnDemand() {
        return configureOnDemand;
    }

    public boolean isConfigurationCache() {
        return configurationCache;
    }

    public LogLevel getConfigurationCacheLogLevel() {
        return configurationCacheLogLevel;
    }

    public boolean isIsolatedProjects() {
        return isolatedProjects;
    }

    /**
     * When {@link  #isIsolatedProjects()} is true, should intermediate tooling models be cached?
     * This is currently true when fetching a tooling model, otherwise false.
     */
    public boolean isIntermediateModelCache() {
        return intermediateModelCache;
    }

    /**
     * When {@link #isParallelProjectExecution()} is true, should Tooling API actions run in parallel?
     */
    public boolean isParallelToolingApiActions() {
        return parallelToolingApiActions;
    }

    /**
     * When {@link  #isIsolatedProjects()} is true, should project state be invalidated when a project it is coupled with changes?
     * This parameter is only used for benchmarking purposes.
     */
    public boolean isInvalidateCoupledProjects() {
        return invalidateCoupledProjects;
    }
}
