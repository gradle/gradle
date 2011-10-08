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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.internal.Factory;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.NoOpFileLock;
import org.gradle.cache.internal.SimpleStateCache;
import org.jfrog.wharf.ivy.cache.WharfCacheManager;
import org.jfrog.wharf.ivy.lock.LockHolder;
import org.jfrog.wharf.ivy.lock.LockHolderFactory;
import org.jfrog.wharf.ivy.marshall.api.MrmMarshaller;
import org.jfrog.wharf.ivy.marshall.api.WharfResolverMarshaller;
import org.jfrog.wharf.ivy.model.ModuleRevisionMetadata;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class IvySettingsFactory implements Factory<IvySettings> {
    private final LockHolderFactory lockHolderFactory;
    private final ArtifactCacheMetaData cacheMetaData;

    public IvySettingsFactory(ArtifactCacheMetaData cacheMetaData, LockHolderFactory lockHolderFactory) {
        this.cacheMetaData = cacheMetaData;
        this.lockHolderFactory = lockHolderFactory;
    }

    public IvySettings create() {
        IvySettings ivySettings = new IvySettings();
        ivySettings.setDefaultCache(cacheMetaData.getCacheDir());
        ivySettings.setDefaultCacheIvyPattern(ArtifactRepositoryContainer.DEFAULT_CACHE_IVY_PATTERN);
        ivySettings.setDefaultCacheArtifactPattern(ArtifactRepositoryContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        ivySettings.setVariable("ivy.log.modules.in.use", "false");

        WharfCacheManager cacheManager = WharfCacheManager.newInstance(ivySettings);
        cacheManager.setLockFactory(lockHolderFactory);
        cacheManager.setMrmMarshaller(new DefaultMrmMarshaller(lockHolderFactory));
        cacheManager.setWharfResolverMarshaller(new DefaultWharfResolverMarshaller(lockHolderFactory));

        ivySettings.setDefaultRepositoryCacheManager(cacheManager);

        ResolutionCacheManager resolutionCacheManager = new TempFileResolutionCacheManager(cacheMetaData.getCacheDir());
        ivySettings.setResolutionCacheManager(resolutionCacheManager);
        return ivySettings;
    }

    private static class DefaultMrmMarshaller implements MrmMarshaller {
        private final LockHolderFactory lockHolderFactory;

        private DefaultMrmMarshaller(LockHolderFactory lockHolderFactory) {
            this.lockHolderFactory = lockHolderFactory;
        }

        public ModuleRevisionMetadata getModuleRevisionMetadata(File file) {
            LockHolder lockHolder = lockHolderFactory.getOrCreateLockHolder(file);
            if (!lockHolder.acquireLock()) {
                throw new RuntimeException(String.format("Could not acquire lock for %s", file));
            }
            try {
                return getCache(file).get();
            } finally {
                lockHolder.releaseLock();
            }
        }

        public void save(ModuleRevisionMetadata mrm, File file) {
            LockHolder lockHolder = lockHolderFactory.getOrCreateLockHolder(file);
            if (!lockHolder.acquireLock()) {
                throw new RuntimeException(String.format("Could not acquire lock for %s", file));
            }
            try {
                getCache(file).set(mrm);
            } finally {
                lockHolder.releaseLock();
            }
        }

        private PersistentStateCache<ModuleRevisionMetadata> getCache(File file) {
            return new SimpleStateCache<ModuleRevisionMetadata>(file, new NoOpFileLock(), new DefaultSerializer<ModuleRevisionMetadata>(getClass().getClassLoader()));
        }

        public String getDataFilePattern() {
            return "[organisation]/[module](/[branch])/wharfdata-[revision].bin";
        }
    }

    private static class DefaultWharfResolverMarshaller implements WharfResolverMarshaller {
        private final LockHolderFactory lockHolderFactory;

        private DefaultWharfResolverMarshaller(LockHolderFactory lockHolderFactory) {
            this.lockHolderFactory = lockHolderFactory;
        }

        public Set<WharfResolverMetadata> getWharfMetadatas(File baseDir) {
            File file = getCacheFile(baseDir);
            LockHolder lockHolder = lockHolderFactory.getOrCreateLockHolder(file);
            if (!lockHolder.acquireLock()) {
                throw new RuntimeException(String.format("Could not acquire lock for %s", file));
            }
            try {
                Set<WharfResolverMetadata> result = getCache(file).get();
                if (result == null) {
                    return new HashSet<WharfResolverMetadata>();
                }
                return result;
            } finally {
                lockHolder.releaseLock();
            }
        }

        public void save(File baseDir, Set<WharfResolverMetadata> wharfResolverMetadatas) {
            File file = getCacheFile(baseDir);
            LockHolder lockHolder = lockHolderFactory.getOrCreateLockHolder(file);
            if (!lockHolder.acquireLock()) {
                throw new RuntimeException(String.format("Could not acquire lock for %s", file));
            }
            try {
                getCache(file).set(wharfResolverMetadatas);
            } finally {
                lockHolder.releaseLock();
            }
        }

        private PersistentStateCache<Set<WharfResolverMetadata>> getCache(File file) {
            return new SimpleStateCache<Set<WharfResolverMetadata>>(file, new NoOpFileLock(), new DefaultSerializer<Set<WharfResolverMetadata>>(getClass().getClassLoader()));
        }

        private File getCacheFile(File baseDir) {
            return new File(baseDir, "wharf/resolvers.bin");
        }
    }
}
