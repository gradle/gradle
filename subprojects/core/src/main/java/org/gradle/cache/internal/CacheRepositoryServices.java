/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.cache.internal;

import com.google.common.hash.Hashing;
import org.gradle.api.Nullable;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.classloader.ClassPathSnapshotter;
import org.gradle.util.GradleVersion;

import java.io.File;

public class CacheRepositoryServices {
    private final File gradleUserHomeDir;
    private final File projectCacheDir;

    public CacheRepositoryServices(File gradleUserHomeDir, @Nullable File projectCacheDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.projectCacheDir = projectCacheDir;
    }

    protected CacheScopeMapping createCacheScopeMapping() {
        return new DefaultCacheScopeMapping(gradleUserHomeDir, projectCacheDir, GradleVersion.current());
    }

    protected CacheRepository createCacheRepository(CacheFactory factory, CacheScopeMapping scopeMapping) {
        return new DefaultCacheRepository(
            scopeMapping,
            factory);
    }

    protected CacheKeyBuilder createCacheKeyBuilder(FileHasher fileHasher, ClassPathSnapshotter snapshotter, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        return new DefaultCacheKeyBuilder(Hashing.md5(), fileHasher, snapshotter, classLoaderHierarchyHasher);
    }
}
