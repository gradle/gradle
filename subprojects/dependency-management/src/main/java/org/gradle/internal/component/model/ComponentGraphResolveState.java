/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.model;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;

import javax.annotation.Nullable;

/**
 * State for a component instance, which is used to perform dependency graph resolution.
 */
public interface ComponentGraphResolveState {
    ComponentIdentifier getId();

    ModuleVersionIdentifier getModuleVersionId();

    /**
     * @return the sources information for this component.
     */
    @Nullable
    ModuleSources getSources();

    /**
     * Returns the set of variants of this component to use for variant aware resolution of the dependency graph nodes. May be empty, in which case selection falls back to the legacy configurations available via {@link ComponentResolveMetadata#getConfiguration(String)}. The component should provide a configuration called {@value Dependency#DEFAULT_CONFIGURATION}.
     *
     * <p>Note: currently, {@link ConfigurationMetadata} is used to represent these variants. This is to help with migration. The set of objects returned by this method may or may not be the same as those returned by {@link ComponentResolveMetadata#getConfigurationNames()}.</p>
     */
    Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal();

    ComponentGraphResolveMetadata getMetadata();

    ComponentResolveMetadata getArtifactResolveMetadata();
}
