/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.resolve.caching;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.Serializable;

public class ComponentMetadataRuleExecutor extends CrossBuildCachingRuleExecutor<ModuleComponentResolveMetadata, ComponentMetadataContext, ModuleComponentResolveMetadata> {

    private final static Transformer<Serializable, ModuleComponentResolveMetadata> KEY_TO_SNAPSHOTTABLE = new Transformer<Serializable, ModuleComponentResolveMetadata>() {
        @Override
        public Serializable transform(ModuleComponentResolveMetadata moduleMetadata) {
            return moduleMetadata.getContentHash().asBigInteger();
        }
    };

    public ComponentMetadataRuleExecutor(CacheRepository cacheRepository,
                                         InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                         ValueSnapshotter snapshotter,
                                         BuildCommencedTimeProvider timeProvider,
                                         Serializer<ModuleComponentResolveMetadata> componentMetadataContextSerializer) {
        super("md-rule", cacheRepository, cacheDecoratorFactory, snapshotter, timeProvider, createValidator(timeProvider), KEY_TO_SNAPSHOTTABLE, componentMetadataContextSerializer);
    }

    private static EntryValidator<ModuleComponentResolveMetadata> createValidator(final BuildCommencedTimeProvider timeProvider) {
        return new CrossBuildCachingRuleExecutor.EntryValidator<ModuleComponentResolveMetadata>() {
            @Override
            public boolean isValid(CachePolicy policy, final CrossBuildCachingRuleExecutor.CachedEntry<ModuleComponentResolveMetadata> entry) {
                long age = timeProvider.getCurrentTime() - entry.getTimestamp();
                final ModuleComponentResolveMetadata result = entry.getResult();
                boolean mustRefreshModule = policy.mustRefreshModule(new SimpleResolvedModuleVersion(result.getModuleVersionId()), age, result.isChanging());
                return !mustRefreshModule;
            }
        };
    }

    private static class SimpleResolvedModuleVersion implements ResolvedModuleVersion {

        private final ModuleVersionIdentifier identifier;

        private SimpleResolvedModuleVersion(ModuleVersionIdentifier identifier) {
            this.identifier = identifier;
        }

        @Override
        public ModuleVersionIdentifier getId() {
            return identifier;
        }
    }
}
