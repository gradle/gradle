/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;

import java.io.Closeable;
import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultGeneratedGradleJarCache implements GeneratedGradleJarCache, Closeable {

    private final PersistentCache cache;
    private final String gradleVersion;

    public DefaultGeneratedGradleJarCache(CacheRepository cacheRepository, String gradleVersion) {
        this.cache = cacheRepository
            .cache(CACHE_KEY)
            .withDisplayName(CACHE_DISPLAY_NAME)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .open();
        this.gradleVersion = gradleVersion;
    }

    @Override
    public File get(final String identifier, final Action<File> creator) {
        final File jarFile = jarFile(identifier);
        if (!jarFile.exists()) {
            cache.useCache("Generating " + jarFile.getName(), new Runnable() {
                public void run() {
                    if (!jarFile.exists()) {
                        creator.execute(jarFile);
                    }
                }
            });
        }
        return jarFile;
    }

    @Override
    public void close() {
        cache.close();
    }

    private File jarFile(String identifier) {
        return new File(cache.getBaseDir(), "gradle-" + identifier + "-" + gradleVersion + ".jar");
    }
}
