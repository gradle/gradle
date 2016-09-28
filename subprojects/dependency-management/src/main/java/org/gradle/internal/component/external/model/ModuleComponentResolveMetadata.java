/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.component.external.model;

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSource;

import java.util.List;
import java.util.Map;

/**
 * The meta-data for a module version that is required during dependency resolution.
 */
public interface ModuleComponentResolveMetadata extends ComponentResolveMetadata {
    /**
     * {@inheritDoc}
     */
    ModuleComponentIdentifier getComponentId();

    /**
     * {@inheritDoc}
     */
    ModuleComponentResolveMetadata withSource(ModuleSource source);

    /**
     * Creates a mutable copy of this metadata.
     *
     * Note that this method can be expensive. Often it is more efficient to use a more specialised mutation method such as {@link #withSource(ModuleSource)} rather than this method.
     */
    MutableModuleComponentResolveMetadata asMutable();

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier);

    @Nullable
    List<ModuleComponentArtifactMetadata> getArtifacts();

    /**
     * Returns this module version as an Ivy-like ModuleDescriptor. This method is here to allow us to migrate away from the Ivy types
     * and will be removed.
     *
     * <p>You should avoid using this method.
     */
    ModuleDescriptorState getDescriptor();

    /**
     * Returns the Ivy-like definitions for the configurations of this module. This method is here to allow us to migrate away from the Ivy model and will be removed.
     */
    Map<String, Configuration> getConfigurationDefinitions();
}
