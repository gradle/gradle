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
import java.util.HashMap;
import java.util.Map;

public class DefaultCacheRepository implements CacheRepository {
    private final GradleVersion version = GradleVersion.current();
    private final File globalCacheDir;
    private final CacheUsage cacheUsage;
    private final CacheFactory factory;

    public DefaultCacheRepository(File userHomeDir, CacheUsage cacheUsage, CacheFactory factory) {
        this.factory = factory;
        this.globalCacheDir = new File(userHomeDir, "caches");
        this.cacheUsage = cacheUsage;
    }

    public CacheBuilder cache(String key) {
        return new PersistentCacheBuilder(key);
    }

    private class PersistentCacheBuilder implements CacheBuilder {
        private final String key;
        private Map<String, ?> properties = Collections.emptyMap();
        private Object target;
        private boolean invalidateOnVersionChange;

        private PersistentCacheBuilder(String key) {
            this.key = key;
        }

        public CacheBuilder withProperties(Map<String, ?> properties) {
            this.properties = properties;
            return this;
        }

        public CacheBuilder forObject(Object target) {
            this.target = target;
            return this;
        }

        public CacheBuilder invalidateOnVersionChange() {
            invalidateOnVersionChange = true;
            return this;
        }

        public PersistentCache open() {
            File cacheBaseDir;
            Map<String, Object> properties = new HashMap<String, Object>(this.properties);
            if (target == null) {
                cacheBaseDir = globalCacheDir;
            } else if (target instanceof Gradle) {
                Gradle gradle = (Gradle) target;
                cacheBaseDir = new File(gradle.getRootProject().getProjectDir(), Project.TMP_DIR_NAME);
            } else if (target instanceof File) {
                File dir = (File) target;
                cacheBaseDir = new File(dir, Project.TMP_DIR_NAME);
            } else {
                throw new IllegalArgumentException(String.format("Cannot create cache for unrecognised domain object %s.", target));
            }
            if (invalidateOnVersionChange) {
                properties.put("gradle.version", version.getVersion());
                cacheBaseDir = new File(cacheBaseDir, "noVersion");
            } else {
                cacheBaseDir = new File(cacheBaseDir, version.getVersion());
            }
            return factory.open(new File(cacheBaseDir, key), cacheUsage, properties);
        }
    }
}
