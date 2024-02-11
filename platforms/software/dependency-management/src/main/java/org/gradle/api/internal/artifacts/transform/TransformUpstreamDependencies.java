/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.internal.Try;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

import javax.annotation.Nullable;

public interface TransformUpstreamDependencies extends TaskDependencyContainer {

    @Nullable
    ConfigurationIdentity getConfigurationIdentity();

    /**
     * Returns a collection containing the future artifacts for the given transform step.
     */
    FileCollection selectedArtifacts();

    /**
     * Computes the finalized dependency artifacts for the given transform step.
     */
    Try<TransformDependencies> computeArtifacts();

    void finalizeIfNotAlready();
}
