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
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds {@link LocalVariantGraphResolveMetadata} instances from {@link ConfigurationInternal}s, while
 * caching intermediary dependency and exclude state.
 */
@ServiceScope(Scope.BuildTree.class)
public interface LocalVariantGraphResolveStateBuilder {

    LocalVariantGraphResolveState create(
        ConfigurationInternal configuration,
        ConfigurationsProvider configurationsProvider,
        ComponentIdentifier componentId,
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
        private final Map<String, DefaultLocalVariantGraphResolveStateBuilder.DependencyState> cache = new HashMap<>();

        public DefaultLocalVariantGraphResolveStateBuilder.DependencyState computeIfAbsent(
            ConfigurationInternal configuration,
            Function<ConfigurationInternal, DependencyState> factory
        ) {
            DependencyState state = cache.get(configuration.getName());
            if (state == null) {
                state = factory.apply(configuration);
                cache.put(configuration.getName(), state);
            }
            return state;
        }

        public void invalidate() {
            cache.clear();
        }
    }

    /**
     * The immutable state of a variant's dependencies and excludes. This type tracks
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
