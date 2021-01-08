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
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.util.BuildCommencedTimeProvider;

import java.time.Duration;

public class ComponentMetadataSupplierRuleExecutor extends CrossBuildCachingRuleExecutor<ModuleVersionIdentifier, ComponentMetadataSupplierDetails, ComponentMetadata> {
    private final static Transformer<String, ModuleVersionIdentifier> KEY_TO_SNAPSHOTTABLE = Object::toString;

    public ComponentMetadataSupplierRuleExecutor(CacheRepository cacheRepository,
                                                 InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                 ValueSnapshotter snapshotter,
                                                 BuildCommencedTimeProvider timeProvider,
                                                 Serializer<ComponentMetadata> componentMetadataSerializer) {
        super("md-supplier", cacheRepository, cacheDecoratorFactory, snapshotter, timeProvider, createValidator(timeProvider), KEY_TO_SNAPSHOTTABLE, componentMetadataSerializer);
    }

    public static EntryValidator<ComponentMetadata> createValidator(final BuildCommencedTimeProvider timeProvider) {
        return (policy, entry) -> {
            Duration age = Duration.ofMillis(timeProvider.getCurrentTime() - entry.getTimestamp());
            final ComponentMetadata result = entry.getResult();
            return !policy.moduleExpiry(new SimpleResolvedModuleVersion(result), age, result.isChanging()).isMustCheck();
        };
    }

    private static class SimpleResolvedModuleVersion implements ResolvedModuleVersion {
        private final ComponentMetadata result;

        public SimpleResolvedModuleVersion(ComponentMetadata result) {
            this.result = result;
        }

        @Override
        public ModuleVersionIdentifier getId() {
            return result.getId();
        }
    }
}
