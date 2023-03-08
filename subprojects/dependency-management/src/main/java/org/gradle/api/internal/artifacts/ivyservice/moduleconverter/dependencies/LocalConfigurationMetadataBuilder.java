/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Builds {@link LocalConfigurationMetadata} instances from {@link ConfigurationInternal}s, while
 * caching intermediary dependency and exclude state.
 */
public interface LocalConfigurationMetadataBuilder {

    /**
     * TODO: Ideally, building a configuration's metadata should not require the parent component
     * reference. We currently need it in order to traverse the configuration hierarchy for building
     * artifact metadata.
     */
    LocalConfigurationMetadata create(
        ConfigurationInternal configuration,
        ConfigurationsProvider configurationsProvider,
        LocalComponentMetadata parent,
        DependencyCache dependencyCache,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    );

    /**
     * A cache of the defined dependencies for dependency configurations. This tracks the cached internal
     * dependency representations for these types so when constructing leaf configuration metadata
     * (resolvable and consumable), these conversions do not need to be executed multiple times.
     */
    class DependencyCache {
        private final Map<String, DefaultLocalConfigurationMetadataBuilder.DependencyState> cache = new HashMap<>();

        public DefaultLocalConfigurationMetadataBuilder.DependencyState computeIfAbsent(
            ConfigurationInternal configuration,
            ComponentIdentifier componentId,
            BiFunction<ConfigurationInternal, ComponentIdentifier, DefaultLocalConfigurationMetadataBuilder.DependencyState> factory
        ) {
            DependencyState state = cache.get(configuration.getName());
            if (state == null) {
                state = factory.apply(configuration, componentId);
                cache.put(configuration.getName(), state);
            }
            return state;
        }

        public void invalidate() {
            cache.clear();
        }
    }

    /**
     * The immutable state of a configuration's dependencies and excludes. This type tracks
     * the internal representations, after they have been converted from their DSL representations.
     */
    class DependencyState {
        public final ImmutableList<LocalOriginDependencyMetadata> dependencies;
        public final ImmutableSet<LocalFileDependencyMetadata> files;
        public final ImmutableList<ExcludeMetadata> excludes;

        public DependencyState(
            ImmutableList<LocalOriginDependencyMetadata> dependencies,
            ImmutableSet<LocalFileDependencyMetadata> files,
            ImmutableList<ExcludeMetadata> excludes
        ) {
            this.dependencies = dependencies;
            this.files = files;
            this.excludes = excludes;
        }
    }
}
