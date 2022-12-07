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
import org.gradle.cache.internal.DecompressionCache;
import org.gradle.cache.internal.DecompressionCacheFactory;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.cache.internal.DefaultDecompressionCache;
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping;
import org.gradle.cache.internal.scopes.DefaultProjectScopedCache;
import org.gradle.cache.scopes.ProjectScopedCache;
import org.gradle.util.GradleVersion;

import java.io.File;

/**
 * A factory that can be used to create {@link DecompressionCache} instances for a particular project.
 *
 * This type exists so that creation of the caches can be done lazily, avoiding the
 * creation of the cache directory (and, by default, the build directory it will be
 * a subdirectory of) until it is actually needed.  We need a named factory type
 * to create a service which can be injected.
 */
public class ProjectScopedDecompressionCacheFactory implements DecompressionCacheFactory {
    private final ProjectLayout projectLayout;
    private final CacheFactory cacheFactory;

    public ProjectScopedDecompressionCacheFactory(ProjectLayout projectLayout, CacheFactory cacheFactory) {
        this.projectLayout = projectLayout;
        this.cacheFactory = cacheFactory;
    }

    @Override
    public DecompressionCache create() {
        File cacheDir = projectLevelCacheDir();
        CacheRepository cacheRepository = new DefaultCacheRepository(new DefaultCacheScopeMapping(cacheDir, GradleVersion.current()), cacheFactory);
        ProjectScopedCache scopedCache = new DefaultProjectScopedCache(cacheDir, cacheRepository);
        return new DefaultDecompressionCache(scopedCache);
    }

    private File projectLevelCacheDir() {
        return projectLayout.getBuildDirectory().file(".cache").get().getAsFile();
    }
}
