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

public class DefaultBuildModelParameters implements BuildModelParameters {

    private final boolean parallelProjectExecution;
    private final boolean configureOnDemand;
    private final boolean configurationCache;
    private final boolean isolatedProjects;
    private final boolean requiresBuildModel;
    private final boolean intermediateModelCache;
    private final boolean parallelToolingApiActions;
    private final boolean invalidateCoupledProjects;
    private final boolean modelAsProjectDependency;

    public DefaultBuildModelParameters(
        boolean parallelProjectExecution,
        boolean configureOnDemand,
        boolean configurationCache,
        boolean isolatedProjects,
        boolean requiresBuildModel,
        boolean intermediateModelCache,
        boolean parallelToolingApiActions,
        boolean invalidateCoupledProjects,
        boolean modelAsProjectDependency
    ) {
        this.parallelProjectExecution = parallelProjectExecution;
        this.configureOnDemand = configureOnDemand;
        this.configurationCache = configurationCache;
        this.isolatedProjects = isolatedProjects;
        this.requiresBuildModel = requiresBuildModel;
        this.intermediateModelCache = intermediateModelCache;
        this.parallelToolingApiActions = parallelToolingApiActions;
        this.invalidateCoupledProjects = invalidateCoupledProjects;
        this.modelAsProjectDependency = modelAsProjectDependency;
    }

    @Override
    public boolean isParallelProjectExecution() {
        return parallelProjectExecution;
    }

    @Override
    public boolean isRequiresBuildModel() {
        return requiresBuildModel;
    }

    @Override
    public boolean isConfigureOnDemand() {
        return configureOnDemand;
    }

    @Override
    public boolean isConfigurationCache() {
        return configurationCache;
    }

    @Override
    public boolean isIsolatedProjects() {
        return isolatedProjects;
    }

    @Override
    public boolean isIntermediateModelCache() {
        return intermediateModelCache;
    }

    @Override
    public boolean isParallelToolingApiActions() {
        return parallelToolingApiActions;
    }

    @Override
    public boolean isInvalidateCoupledProjects() {
        return invalidateCoupledProjects;
    }

    @Override
    public boolean isModelAsProjectDependency() {
        return modelAsProjectDependency;
    }
}
