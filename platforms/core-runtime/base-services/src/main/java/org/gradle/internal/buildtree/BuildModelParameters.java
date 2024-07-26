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

@ServiceScope(Scope.BuildTree.class)
public interface BuildModelParameters {

    boolean isParallelProjectExecution();

    /**
     * Will the build model, that is the configured Gradle and Project objects, be required during the build execution?
     *
     * <p>When the build model is not required, certain state can be discarded or not created.
     */
    boolean isRequiresBuildModel();

    boolean isConfigureOnDemand();

    boolean isConfigurationCache();

    boolean isIsolatedProjects();

    /**
     * When {@link  #isIsolatedProjects()} is true, should intermediate tooling models be cached?
     * This is currently true when fetching a tooling model, otherwise false.
     */
    boolean isIntermediateModelCache();

    /**
     * When {@link #isParallelProjectExecution()} is true, should Tooling API actions run in parallel?
     */
    boolean isParallelToolingApiActions();

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
}
