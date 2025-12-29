/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.component.model.IvyArtifactName;
import org.jspecify.annotations.Nullable;

/**
 * Represents the result of successfully applying substitution rules to a dependency metadata.
 */
public interface SubstitutionResult {

    /**
     * The new target component selector, or null if a new target selector was not configured.
     */
    @Nullable
    ComponentSelector getTarget();

    /**
     * The new target artifacts, or null if no new target artifacts were configured.
     */
    @Nullable
    ImmutableList<IvyArtifactName> getArtifacts();

    /**
     * Descriptors describing the substitution rules that produced this result.
     */
    ImmutableList<ComponentSelectionDescriptorInternal> getRuleDescriptors();

}
