/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.cache;

import org.gradle.api.file.ProjectLayout;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping;
import org.gradle.cache.internal.scopes.DefaultProjectScopedCache;
import org.gradle.cache.scopes.ProjectScopedCache;
import org.gradle.util.GradleVersion;

import javax.annotation.concurrent.GuardedBy;
import java.io.File;

/**
 * A lazy implementation of {@link AbstractManagedDecompressionCacheFactory} that creates
 * decompression caches for a project.
 *
 * This implementation provides for lazy creation of the caches, avoiding the
 * need to read the cache directory (and, by default, the build directory it will be
 * a subdirectory of) until we are actually creating a cache.  We need a named factory type
 * to create a service which can be injected.
 */
public final class ProjectScopedDecompressionCacheFactory extends AbstractManagedDecompressionCacheFactory {
    private final ProjectLayout projectLayout;
    private final CacheFactory cacheFactory;

    private final Object[] lock = new Object[0];
    @GuardedBy("lock")
    private volatile ProjectScopedCache scopedCache;

    public ProjectScopedDecompressionCacheFactory(ProjectLayout projectLayout, CacheFactory cacheFactory) {
        this.projectLayout = projectLayout;
        this.cacheFactory = cacheFactory;
    }

    @Override
    protected ProjectScopedCache getScopedCache() {
        if (scopedCache == null) {
            synchronized (lock) {
                if (scopedCache == null) {
                    File cacheDir = getCacheDir();
                    CacheRepository cacheRepository = new DefaultCacheRepository(new DefaultCacheScopeMapping(cacheDir, GradleVersion.current()), cacheFactory);
                    scopedCache = new DefaultProjectScopedCache(cacheDir, cacheRepository);
                }
            }
        }
        return scopedCache;
    }

    private File getCacheDir() {
        return projectLayout.getBuildDirectory().file(".cache").get().getAsFile();
    }
}
