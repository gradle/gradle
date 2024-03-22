/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.Set;

public interface ModuleComponentGraphResolveMetadata extends ComponentGraphResolveMetadata {
    /**
     * Was the metadata artifact for this component missing? When true, the metadata for this component was generated using some defaults.
     */
    boolean isMissing();

    /**
     * Returns the names of all legacy configurations for this component.
     * May be empty, in which case the component should provide at least one variant via {@link ComponentGraphResolveMetadata#getVariantsForGraphTraversal()}.
     */
    Set<String> getConfigurationNames();

    /**
     * Get a configuration by name.
     *
     * <p>Configurations are a legacy concept. Only ivy components should expose configurations.</p>
     */
    @Nullable
    ConfigurationGraphResolveMetadata getConfiguration(String name);
}
