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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.IvySettingsAware;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.util.TimeProvider;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.File;
import java.io.Serializable;

public class DefaultModuleDescriptorCache implements ModuleDescriptorCache, IvySettingsAware {
    private final TimeProvider timeProvider;
    private final ArtifactCacheMetaData cacheMetadata;
    private final CacheLockingManager cacheLockingManager;
    
    private final ModuleDescriptorStore moduleDescriptorStore;
    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> cache;

    private IvySettings ivySettings;

    public DefaultModuleDescriptorCache(ArtifactCacheMetaData cacheMetadata, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.cacheMetadata = cacheMetadata;

        // TODO:DAZ inject this
        moduleDescriptorStore = new ModuleDescriptorStore(new ModuleDescriptorFileStore(cacheMetadata));
    }

    // TODO:DAZ This is a bit nasty
    public void setSettings(IvySettings settings) {
        this.ivySettings = settings;
    }

    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> initCache() {
        File artifactResolutionCacheFile = new File(cacheMetadata.getCacheDir(), "module-metadata.bin");
        FileLock artifactResolutionCacheLock = cacheLockingManager.getCacheMetadataFileLock(artifactResolutionCacheFile);
        return new BTreePersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry>(artifactResolutionCacheFile, artifactResolutionCacheLock,
                new DefaultSerializer<ModuleDescriptorCacheEntry>(ModuleDescriptorCacheEntry.class.getClassLoader()));
    }

    public CachedModuleDescriptor getCachedModuleDescriptor(DependencyResolver resolver, ModuleRevisionId moduleRevisionId) {
        ModuleDescriptorCacheEntry moduleDescriptorCacheEntry = getCache().get(createKey(resolver, moduleRevisionId));
        if (moduleDescriptorCacheEntry == null) {
            // TODO:DAZ Could do something smart if the entry is missing but the file is present?
            return null;
        }
        ModuleDescriptor descriptor = moduleDescriptorStore.getModuleDescriptor(resolver, moduleRevisionId, ivySettings);
        return new DefaultCachedModuleDescriptor(moduleDescriptorCacheEntry, descriptor, timeProvider);
    }

    public void cacheModuleDescriptor(DependencyResolver resolver, ModuleDescriptor moduleDescriptor, boolean isChanging) {
        // TODO:DAZ Cache will already be locked, due to prior call to getCachedModuleDescriptor. This locking should be more explicit
        moduleDescriptorStore.putModuleDescriptor(resolver, moduleDescriptor);
        getCache().put(createKey(resolver, moduleDescriptor.getModuleRevisionId()), createEntry(isChanging));
    }

    private RevisionKey createKey(DependencyResolver resolver, ModuleRevisionId moduleRevisionId) {
        return new RevisionKey(resolver, moduleRevisionId);
    }

    private ModuleDescriptorCacheEntry createEntry(boolean changing) {
        return new ModuleDescriptorCacheEntry(changing, timeProvider);
    }

    private static class RevisionKey implements Serializable {
        private final String resolverId;
        private final String artifactId;

        private RevisionKey(DependencyResolver resolver, ModuleRevisionId moduleRevisionId) {
            this.resolverId = new WharfResolverMetadata(resolver).getId();
            this.artifactId = moduleRevisionId.encodeToString();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof RevisionKey)) {
                return false;
            }
            RevisionKey other = (RevisionKey) o;
            return resolverId.equals(other.resolverId) && artifactId.equals(other.artifactId);
        }

        @Override
        public int hashCode() {
            return resolverId.hashCode() ^ artifactId.hashCode();
        }
    }

}
