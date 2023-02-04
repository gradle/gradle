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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Immutable metadata for a component instance (ie version of a component) that is used to perform dependency graph resolution.
 *
 * <p>Note this interface exposes only information that is required for dependency graph resolution. In particular, it does not provide any information about the available artifacts of this
 * component or its variants, as this may be expensive to calculate and is only required in specific cases.
 * Information about the artifacts can be accessed via the methods of {@link ComponentGraphResolveState}.</p>
 *
 * <p>Implementations must be immutable, thread safe, "fast" (ie should not run user code, or touch the file system or network etc) and "reliable" (ie should not fail)
 * Expensive operations should live on {@link ComponentGraphResolveState} instead. Note that as a transition step, not all implementations currently honor this contract.</p>
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
     */
    Optional<List<? extends VariantGraphResolveMetadata>> getVariantsForGraphTraversal();

    Set<String> getConfigurationNames();

    @Nullable
    ConfigurationGraphResolveMetadata getConfiguration(String name);

    List<? extends VirtualComponentIdentifier> getPlatformOwners();

    @Nullable
    String getStatus();
}
