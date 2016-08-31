/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;

import java.util.List;
import java.util.Map;

public interface MutableModuleComponentResolveMetadata {
    /**
     * The identifier for this component
     */
    ModuleComponentIdentifier getComponentId();

    /**
     * The module version associated with this module.
     */
    ModuleVersionIdentifier getId();

    /**
     * Creates an immutable copy of this meta-data.
     */
    ModuleComponentResolveMetadata asImmutable();

    /**
     * Sets the component id and legacy module version id
     */
    void setComponentId(ModuleComponentIdentifier componentId);

    boolean isChanging();
    void setChanging(boolean changing);

    String getStatus();
    void setStatus(String status);

    List<String> getStatusScheme();
    void setStatusScheme(List<String> statusScheme);

    ModuleSource getSource();
    void setSource(ModuleSource source);

    /**
     * Returns this module version as an Ivy-like ModuleDescriptor. This method is here to allow us to migrate away from the Ivy types
     * and will be removed.
     *
     * <p>You should avoid using this method.
     */
    ModuleDescriptorState getDescriptor();

    List<? extends DependencyMetadata> getDependencies();

    /**
     * Replaces the dependencies of this module version.
     */
    void setDependencies(Iterable<? extends DependencyMetadata> dependencies);

    /**
     * Returns the Ivy-like definitions for the configurations of this module. This method is here to allow us to migrate away from the Ivy model and will be removed.
     */
    Map<String, Configuration> getConfigurationDefinitions();

    @Nullable
    List<ModuleComponentArtifactMetadata> getArtifacts();

    /**
     * Replaces the artifacts of this module version.
     */
    void setArtifacts(Iterable<? extends ModuleComponentArtifactMetadata> artifacts);

    /**
     * Creates an artifact for this module. Does not mutate this metadata.
     */
    ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier);
}
