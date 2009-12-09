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
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class DefaultCacheRepository implements CacheRepository {
    private final GradleVersion version = new GradleVersion();
    private final File globalCacheDir;
    private final CacheUsage cacheUsage;
    private final CacheFactory factory;

    public DefaultCacheRepository(File userHomeDir, CacheUsage cacheUsage, CacheFactory factory) {
        this.factory = factory;
        this.globalCacheDir = new File(userHomeDir, String.format("caches/%s", version.getVersion()));
        this.cacheUsage = cacheUsage;
    }

    public PersistentCache getCacheFor(Object target, String key, Map<String, ?> properties) {
        if (target instanceof Gradle) {
            Gradle gradle = (Gradle) target;
            File buildTmpDir = new File(gradle.getRootProject().getProjectDir(), Project.TMP_DIR_NAME);
            File cacheDir = new File(buildTmpDir, String.format("%s/%s", version.getVersion(), key));
            return factory.open(cacheDir, cacheUsage, properties);
        }
        throw new IllegalArgumentException(String.format("Cannot create cache for domain object %s.", target));
    }

    public PersistentCache getGlobalCache(String key, Map<String, ?> properties) {
        return factory.open(new File(globalCacheDir, key), cacheUsage, properties);
    }

    public PersistentCache getGlobalCache(String key) {
        return getGlobalCache(key, Collections.EMPTY_MAP);
    }

    public PersistentCache getCacheFor(Object target, String key) {
        return getCacheFor(target, key, Collections.EMPTY_MAP);
    }
}
