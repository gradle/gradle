/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.internal.component.model.VariantGraphResolveMetadata;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Component metadata for external components.
 *
 * <p>Like {@link ComponentGraphResolveMetadata}, methods on this interface should be thread safe and fast -- meaning
 * they do not run user code or execute network requests. This is not currently the case. Instead, that logic should
 * be migrated to {@link ExternalComponentGraphResolveState}</p>
 */
public interface ExternalComponentGraphResolveMetadata extends ComponentGraphResolveMetadata {

    /**
     * Returns the set of variants of this component to use for variant aware resolution of the dependency graph nodes.
     * May be empty, in which case selection falls back to an ecosystem-specific selection strategy.
     */
    List<? extends VariantGraphResolveMetadata> getVariantsForGraphTraversal();

    /**
     * Returns the names of all legacy configurations for this component.
     * May be empty, in which case the component should provide at least one variant via {@link #getVariantsForGraphTraversal()}.
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
