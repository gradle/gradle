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

package org.gradle.plugin.internal;

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.Factory;
import org.gradle.internal.Supplier;
import org.gradle.internal.Suppliers;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.messaging.serialize.BaseSerializerFactory;
import org.gradle.plugin.resolve.internal.*;

public abstract class PluginResolvers {

    public static PluginResolver jcenterGradleOfficial(Instantiator instantiator, DependencyResolutionServices dependencyResolutionServices, final CacheRepository cacheRepository) {
        Supplier<PersistentIndexedCache<PluginRequest, String>> cacheSupplier = Suppliers.wrap(
                Suppliers.ofQuietlyClosed(new Factory<PersistentCache>() {
                    public PersistentCache create() {
                        return cacheRepository.cache("plugins").withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive)).open();
                    }
                }),
                new Transformer<PersistentIndexedCache<PluginRequest, String>, PersistentCache>() {
                    public PersistentIndexedCache<PluginRequest, String> transform(PersistentCache original) {
                        PersistentIndexedCacheParameters<PluginRequest, String> cacheParams = new PersistentIndexedCacheParameters<PluginRequest, String>("jcenter", new PluginRequestSerializer(), BaseSerializerFactory.STRING_SERIALIZER);
                        return original.createCache(cacheParams);
                    }
                });

        final JCenterPluginMapper mapper = new JCenterPluginMapper(cacheSupplier);

        return new ModuleMappingPluginResolver("jcenter plugin resolver", dependencyResolutionServices, instantiator, mapper, new JCenterRepositoryConfigurer()) {
            @Override
            public String getDescriptionForNotFoundMessage() {
                return String.format("Gradle Bintray Plugin Repository (listing: %s)", mapper.getBintrayRepoUrl());
            }
        };
    }
}
