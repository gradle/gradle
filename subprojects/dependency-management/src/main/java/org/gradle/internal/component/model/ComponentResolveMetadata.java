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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The meta-data for a component instance that is required during dependency resolution.
 *
 * <p>Implementations of this type should be immutable and thread safe.</p>
 */
public interface ComponentResolveMetadata {
    List<String> DEFAULT_STATUS_SCHEME = Arrays.asList("integration", "milestone", "release");

    /**
     * Returns the identifier for this component.
     */
    ComponentIdentifier getComponentId();

    /**
     * Returns the module version identifier for this component. Currently this reflects the (group, module, version) that was used to request this component.
     *
     * <p>This is a legacy identifier and is here while we transition the meta-data away from ivy-like
     * module versions to the more general component instances. Currently, the module version and component identifiers are used interchangeably. However, over
     * time more things will use the component identifier. At some point, the module version identifier will become optional for a component.
     */
    ModuleVersionIdentifier getId();

    /**
     * Returns the source (eg location) for this component.
     */
    ModuleSource getSource();

    /**
     * Creates a copy of this meta-data with the given source.
     */
    ComponentResolveMetadata withSource(ModuleSource source);

    List<? extends DependencyMetadata> getDependencies();

    /**
     * Returns the names of all of the configurations for this component.
     */
    Set<String> getConfigurationNames();

    List<? extends ConfigurationMetadata> getConsumableConfigurationsHavingAttributes();

    /**
     * Locates the configuration with the given name, if any.
     */
    @Nullable
    ConfigurationMetadata getConfiguration(String name);

    boolean isGenerated();

    boolean isChanging();

    String getStatus();

    List<String> getStatusScheme();
}
