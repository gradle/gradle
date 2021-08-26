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

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class BuildModelParameters {
    private final boolean configureOnDemand;
    private final boolean configurationCache;
    private final boolean isolatedProjects;
    private final boolean requiresBuildModel;
    private final boolean parallelToolingApiActions;

    public BuildModelParameters(boolean configureOnDemand, boolean configurationCache, boolean isolatedProjects, boolean requiresBuildModel, boolean parallelToolingApiActions) {
        this.configureOnDemand = configureOnDemand;
        this.configurationCache = configurationCache;
        this.isolatedProjects = isolatedProjects;
        this.requiresBuildModel = requiresBuildModel;
        this.parallelToolingApiActions = parallelToolingApiActions;
    }

    /**
     * Will the build model, that is the configured Gradle and Project objects, be required during the build execution?
     *
     * <p>When the build model is not required, certain state can be discarded.
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

    public boolean isIsolatedProjects() {
        return isolatedProjects;
    }

    /**
     * Force parallel tooling API actions? When true, always use parallel execution, when false use a default value.
     */
    public boolean isParallelToolingApiActions() {
        return parallelToolingApiActions;
    }
}
