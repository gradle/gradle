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

package org.gradle.vcs.internal.resolver;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.internal.VcsDirectoryLayout;

import javax.annotation.Nullable;
import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class PersistentVcsMetadataCache implements Stoppable {
    private final PersistentCache cache;
    private final PersistentIndexedCache<String, File> workingDirCache;

    public PersistentVcsMetadataCache(VcsDirectoryLayout directoryLayout, CacheRepository cacheRepository) {
        cache = cacheRepository
            .cache(directoryLayout.getMetadataDir())
            .withDisplayName("VCS metadata")
            .withLockOptions(mode(FileLockManager.LockMode.None)) // Don't need to lock anything until we use the caches
            .open();
        workingDirCache = cache.createCache("workingDirs", String.class, new BaseSerializerFactory().getSerializerFor(File.class));
    }

    @Override
    public void stop() {
        cache.close();
    }

    @Nullable
    public File getWorkingDirForSelector(final VersionControlSpec spec, final VersionConstraint constraint) {
        return cache.useCache(new Factory<File>() {
            @Nullable
            @Override
            public File create() {
                return workingDirCache.get(constraintCacheKey(spec, constraint));
            }
        });
    }

    public void putWorkingDirForSelector(final VersionControlSpec spec, final VersionConstraint constraint, final File workingDirForVersion) {
        cache.useCache(new Runnable() {
            @Override
            public void run() {
                workingDirCache.put(constraintCacheKey(spec, constraint), workingDirForVersion);
            }
        });
    }

    private String constraintCacheKey(VersionControlSpec spec, VersionConstraint constraint) {
        if (constraint.getBranch() != null) {
            return spec.getUniqueId() + ":b:" + constraint.getBranch();
        }
        return spec.getUniqueId() + ":v:" + constraint.getRequiredVersion();
    }
}
