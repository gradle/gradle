/*
 * Copyright 2007-2008 the original author or authors.
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
 
package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.plugins.lock.NoLockStrategy;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.gradle.api.DependencyManager;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class LocalReposCacheHandler {
    private File buildResolverDir;

    private DefaultRepositoryCacheManager cacheManagerInternal;

    public LocalReposCacheHandler() {

    }

    public LocalReposCacheHandler(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir;
    }

    public RepositoryCacheManager getCacheManager() {
        if (cacheManagerInternal == null) {
            cacheManagerInternal = new DefaultRepositoryCacheManager();
            cacheManagerInternal.setBasedir(new File(buildResolverDir, DependencyManager.DEFAULT_CACHE_DIR_NAME));
            cacheManagerInternal.setName(DependencyManager.DEFAULT_CACHE_NAME);
            cacheManagerInternal.setUseOrigin(true);
            cacheManagerInternal.setLockStrategy(new NoLockStrategy());
            cacheManagerInternal.setIvyPattern(DependencyManager.DEFAULT_CACHE_IVY_PATTERN);
            cacheManagerInternal.setArtifactPattern(DependencyManager.DEFAULT_CACHE_ARTIFACT_PATTERN);
        }
        return cacheManagerInternal;
    }

    public File getBuildResolverDir() {
        return buildResolverDir;
    }

    public void setBuildResolverDir(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir;
    }

    public DefaultRepositoryCacheManager getCacheManagerInternal() {
        return cacheManagerInternal;
    }

    public void setCacheManagerInternal(DefaultRepositoryCacheManager cacheManagerInternal) {
        this.cacheManagerInternal = cacheManagerInternal;
    }
}
