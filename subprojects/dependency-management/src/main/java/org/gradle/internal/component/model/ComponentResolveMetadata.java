/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The meta-data for a component instance that is required during dependency resolution.
 *
 * <p>Note that this type is being replaced by several other interfaces that separate out the data and state required at various stages of dependency resolution.
 * You should try to use those interfaces instead of using this interface or introduce a new interface that provides a view over this type but exposes only the
 * data required.
 * </p>
 *
 * @see ComponentGraphResolveState
 * @see ComponentGraphResolveMetadata
 * @see ComponentArtifactResolveState
 */
public interface ComponentResolveMetadata extends HasAttributes {
    List<String> DEFAULT_STATUS_SCHEME = Arrays.asList("integration", "milestone", "release");

    /**
     * Returns the identifier for this component.
     */
    ComponentIdentifier getId();

    /**
     * Returns the module version identifier for this component. Currently, this reflects the (group, module, version) that was used to request this component.
     *
     * <p>This is a legacy identifier and is here while we transition the meta-data away from ivy-like
     * module versions to the more general component instances. Currently, the module version and component identifiers are used interchangeably. However, over
     * time more things will use the component identifier. At some point, the module version identifier will become optional for a component.
     */
    ModuleVersionIdentifier getModuleVersionId();

    /**
     * @return the sources information for this component.
     */
    ModuleSources getSources();

    /**
     * Returns the schema used by this component.
     */
    AttributesSchemaInternal getAttributesSchema();

    /**
     * Returns the names of all legacy configurations for this component. May be empty, in which case the component should provide at least one variant via {@link ComponentGraphResolveMetadata#getVariantsForGraphTraversal()}.
     */
    Set<String> getConfigurationNames();

    /**
     * Returns true when this metadata represents the default metadata provided for components with missing metadata files.
     */
    boolean isMissing();

    boolean isChanging();

    @Nullable
    String getStatus();

    @Nullable
    List<String> getStatusScheme();

    ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners();

    @Override
    ImmutableAttributes getAttributes();
}
