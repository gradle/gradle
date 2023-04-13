/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;

import javax.annotation.Nullable;
import java.time.Duration;

public interface ModuleMetadataCache {
    CachedMetadata cacheMissing(ModuleComponentRepository<?> repository, ModuleComponentIdentifier id);

    CachedMetadata cacheMetaData(ModuleComponentRepository<?> repository, ModuleComponentIdentifier id, ModuleComponentResolveMetadata metaData);

    CachedMetadata getCachedModuleDescriptor(ModuleComponentRepository<?> repository, ModuleComponentIdentifier id);

    interface CachedMetadata {
        ResolvedModuleVersion getModuleVersion();

        ModuleComponentResolveMetadata getMetadata();

        Duration getAge();

        boolean isMissing();

        ModuleSources getModuleSources();

        /**
         * The metadata after being processed by component metadata rules.
         * Will be null the first time an entry is read from the filesystem cache during a build invocation.
         *
         * @param key the hash of the rules
         */
        @Nullable
        ModuleComponentGraphResolveState getProcessedMetadata(int key);

        /**
         * Set the processed metadata to be cached in-memory only.
         */
        void putProcessedMetadata(int key, ModuleComponentGraphResolveState state);

        /**
         * Returns a copy of this cached metadata where the module metadata is safe to store
         * in-memory, cross-build. That is to say it shouldn't contain any reference to projects,
         * for example.
         */
        ModuleMetadataCache.CachedMetadata dehydrate();
    }
}
