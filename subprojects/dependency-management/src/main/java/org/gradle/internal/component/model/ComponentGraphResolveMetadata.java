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
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Immutable metadata for a component instance, which is used to perform dependency graph resolution.
 */
public interface ComponentGraphResolveMetadata {
    /**
     * Returns the identifier for this component.
     */
    ComponentIdentifier getId();

    ModuleVersionIdentifier getModuleVersionId();

    @Nullable
    AttributesSchemaInternal getAttributesSchema();

    boolean isChanging();

    /**
     * Returns the set of variants of this component to use for variant aware resolution of the dependency graph nodes. May be empty, in which case selection falls back to the legacy configurations available via {@link #getConfiguration(String)}. The component should provide a configuration called {@value Dependency#DEFAULT_CONFIGURATION}.
     *
     * <p>Note: currently, {@link ConfigurationMetadata} is used to represent these variants. This is to help with migration. The set of objects returned by this method may or may not be the same as those returned by {@link #getConfigurationNames()}.</p>
     */
    Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal();

    Set<String> getConfigurationNames();

    @Nullable
    ConfigurationMetadata getConfiguration(String name);

    ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners();

    @Nullable
    String getStatus();
}
