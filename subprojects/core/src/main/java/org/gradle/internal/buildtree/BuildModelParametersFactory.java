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

import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Determines Gradle features and behaviors needed for executing a build action over a build tree
 * based on the build action requirements and build options requested by the user.
 */
@ServiceScope(Scope.Global.class)
public interface BuildModelParametersFactory {

    /**
     * Determines Gradle features and behaviors that are required or requested by the build action.
     * <p>
     * This includes features like Configuration Cache, Isolated Projects
     * and their sub-behaviors (e.g., caching and parallelism controls).
     * <p>
     * The requirements are also evaluated for consistency, and the build is failed if they are contradictory.
     *
     * @throws org.gradle.api.GradleException if the requirements are contradictory
     */
    BuildModelParameters parametersForRootBuildTree(BuildActionModelRequirements requirements, InternalOptions internalOptions);

    /**
     * Determines Gradle features and behaviors that are required or requested by the build action of a nested build tree.
     * <p>
     * Nested build trees do not support some major features like Configuration Cache or Isolated Projects.
     */
    BuildModelParameters parametersForNestedBuildTree(BuildActionModelRequirements requirements);
}
