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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.util.BuildCommencedTimeProvider;

public class ComponentMetadataRuleExecutor extends CrossBuildCachingRuleExecutor<ModuleComponentResolveMetadata, ComponentMetadataContext, ModuleComponentResolveMetadata> {

    private static Transformer<Object, ModuleComponentResolveMetadata> getKeyToSnapshotableTransformer() {
        return moduleMetadata -> moduleMetadata.getSources().withSource(ModuleDescriptorHashModuleSource.class, source -> {
            return source.map(metadataFileSource -> metadataFileSource.getDescriptorHash().toString())
                .orElseThrow(() -> new RuntimeException("Cannot find original content hash"));
        });
    }

    private final Serializer<ModuleComponentResolveMetadata> componentMetadataContextSerializer;

    public ComponentMetadataRuleExecutor(CacheRepository cacheRepository,
                                         InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                         ValueSnapshotter snapshotter,
                                         BuildCommencedTimeProvider timeProvider,
                                         Serializer<ModuleComponentResolveMetadata> componentMetadataContextSerializer) {
        super("md-rule", cacheRepository, cacheDecoratorFactory, snapshotter, timeProvider, createValidator(timeProvider), getKeyToSnapshotableTransformer(), componentMetadataContextSerializer);
        this.componentMetadataContextSerializer = componentMetadataContextSerializer;
    }

    public Serializer<ModuleComponentResolveMetadata> getComponentMetadataContextSerializer() {
        return componentMetadataContextSerializer;
    }

    private static EntryValidator<ModuleComponentResolveMetadata> createValidator(final BuildCommencedTimeProvider timeProvider) {
        return (policy, entry) -> {
            long age = timeProvider.getCurrentTime() - entry.getTimestamp();
            final ModuleComponentResolveMetadata result = entry.getResult();
            boolean mustRefreshModule = policy.mustRefreshModule(new SimpleResolvedModuleVersion(result.getModuleVersionId()), age, result.isChanging());
            return !mustRefreshModule;
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
