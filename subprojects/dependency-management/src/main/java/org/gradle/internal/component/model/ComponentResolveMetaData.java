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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;

import java.util.List;
import java.util.Set;

/**
 * The meta-data for a component instance that is required during dependency resolution.
 */
public interface ComponentResolveMetaData {
    /**
     * Returns the identifier for this component.
     */
    ComponentIdentifier getComponentId();

    /**
     * Returns the module version identifier for this component. This is a legacy identifier and is here while we transition the meta-data away from ivy-like
     * module versions to the more general component instances. Currently, the module version and component identifiers are used interchangeably. However, over
     * time more things will use the component identifier. At some point, the module version identifier will become optional for a component.
     */
    ModuleVersionIdentifier getId();

    /**
     * Returns the source (eg location) for this component.
     */
    ModuleSource getSource();

    /**
     * Makes a copy of this meta-data with the given source.
     */
    ComponentResolveMetaData withSource(ModuleSource source);

    /**
     * Returns this module version as an Ivy ModuleDescriptor. This method is here to allow us to migrate away from the Ivy types
     * and will be removed.
     *
     * <p>You should avoid using this method.
     */
    ModuleDescriptor getDescriptor();

    List<DependencyMetaData> getDependencies();

    /**
     * Locates the configuration with the given name, if any.
     */
    @Nullable
    ConfigurationMetaData getConfiguration(String name);

    /**
     * Converts the given Ivy artifact to the corresponding artifact meta-data. This method is here to allow us to migrate away from the Ivy types and
     * will be removed.
     */
    ComponentArtifactMetaData artifact(Artifact artifact);

    /**
     * Returns the known artifacts for this component. There may be additional component available that are not included in this set.
     */
    Set<? extends ComponentArtifactMetaData> getArtifacts();

    boolean isGenerated();

    boolean isChanging();

    String getStatus();

    List<String> getStatusScheme();
}
