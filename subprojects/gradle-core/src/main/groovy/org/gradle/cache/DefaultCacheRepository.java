/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultCacheRepository implements CacheRepository {
    private static Logger logger = Logging.getLogger(DefaultCacheRepository.class);

    private final GradleVersion version = new GradleVersion();
    private final File globalCacheDir;
    private final File userHomeDir;
    private final CacheUsage cacheUsage;

    public DefaultCacheRepository(File userHomeDir, CacheUsage cacheUsage) {
        this.userHomeDir = userHomeDir;
        this.globalCacheDir = new File(this.userHomeDir, "caches");
        this.cacheUsage = cacheUsage;
    }

    public PersistentCache getCacheFor(Object target, String key, Map<String, ?> properties) {
        if (target instanceof Gradle) {
            Gradle gradle = (Gradle) target;
            File buildTmpDir = new File(gradle.getRootProject().getProjectDir(), Project.TMP_DIR_NAME);
            File cacheDir = new File(buildTmpDir, key);
            return new DefaultPersistentCache(cacheDir, cacheUsage, addVersion(properties));
        }
        throw new IllegalArgumentException(String.format("Cannot create cache for domain object %s.", target));
    }

    public <K, V> PersistentIndexedCache<K, V> getIndexedCacheFor(Object target, String key, Map<String, ?> properties) {
        return new DefaultPersistentIndexedCache<K,V>(getCacheFor(target, key, properties));
    }

    public PersistentCache getGlobalCache(String key, Map<String, ?> properties) {
        cleanCachesFromOldVersions();
        return new DefaultPersistentCache(new File(globalCacheDir, key), cacheUsage, addVersion(properties));
    }

    public <K, V> PersistentIndexedCache<K, V> getIndexedGlobalCache(String key, Map<String, ?> properties) {
        return new DefaultPersistentIndexedCache<K,V>(getGlobalCache(key, properties));
    }

    private Map<String, ?> addVersion(Map<String, ?> properties) {
        Map<String, Object> newProperties = new HashMap<String, Object>(properties);
        newProperties.put("version", version.getVersion());
        return newProperties;
    }

    private void cleanCachesFromOldVersions() {
        File oldCache = new File(userHomeDir, "scriptCache");
        if (oldCache.isDirectory()) {
            logger.info("Removing old cache directory " + oldCache);
            GFileUtils.deleteDirectory(oldCache);
        }
    }
}
