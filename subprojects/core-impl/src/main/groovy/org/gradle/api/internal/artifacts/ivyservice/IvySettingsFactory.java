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

import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.internal.Factory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.jfrog.wharf.ivy.cache.WharfCacheManager;

import static org.gradle.cache.CacheBuilder.VersionStrategy;

public class IvySettingsFactory implements Factory<IvySettings> {
    private final CacheRepository cacheRepository;

    public IvySettingsFactory(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    public IvySettings create() {
        IvySettings ivySettings = new IvySettings();
        PersistentCache cache = cacheRepository.store("artifacts").withVersionStrategy(VersionStrategy.SharedCache).open();
        ivySettings.setDefaultCache(cache.getBaseDir());
        ivySettings.setDefaultCacheIvyPattern(ArtifactRepositoryContainer.DEFAULT_CACHE_IVY_PATTERN);
        ivySettings.setDefaultCacheArtifactPattern(ArtifactRepositoryContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        ivySettings.setVariable("ivy.log.modules.in.use", "false");
        ivySettings.setDefaultRepositoryCacheManager(WharfCacheManager.newInstance(ivySettings));
        return ivySettings;
    }
}
