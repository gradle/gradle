/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.DefaultCacheBuilder;
import org.gradle.cache.internal.scopes.NamedCacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.util.GradleVersion;

import java.io.File;

public class JvmInstallationMetadataCacheBuildFactory {

    private final CacheScopeMapping cacheScopeMapping;
    private final CacheFactory factory;
    private final File globalCacheDir;

    public JvmInstallationMetadataCacheBuildFactory(CacheFactory factory, File globalCacheDir) {
        this.cacheScopeMapping = new NamedCacheScopeMapping(globalCacheDir, GradleVersion.current().getVersion());
        this.factory = factory;
        this.globalCacheDir = globalCacheDir;
    }

    public CacheBuilder createBuilder() {
        File baseDirForCache = cacheScopeMapping.getBaseDirectory(globalCacheDir, "toolchainsMetadata", VersionStrategy.CachePerVersion);
        return new DefaultCacheBuilder(factory, baseDirForCache)
            .withDisplayName("Toolchains Metadata")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand);
    }
}
