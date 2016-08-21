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

package org.gradle.internal.classpath;

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.file.JarCache;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer {
    private final PersistentCache cache;
    private final JarCache jarCache;
    private final CacheScopeMapping cacheScopeMapping;
    private final Spec<File> alreadyCachedSpec;

    public DefaultCachedClasspathTransformer(CacheRepository cacheRepository, JarCache jarCache, CacheScopeMapping cacheScopeMapping) {
        this.jarCache = jarCache;
        this.cacheScopeMapping = cacheScopeMapping;
        this.cache = cacheRepository
            .cache("jars-1")
            .withDisplayName("jars")
            .withCrossVersionCache()
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .open();
        this.alreadyCachedSpec = new AlreadyCachedSpec();
    }

    @Override
    public ClassPath transform(ClassPath classPath) {
        return DefaultClassPath.of(CollectionUtils.collect(classPath.getAsFiles(), new Transformer<File, File>() {
            @Override
            public File transform(final File original) {
                if (original.isFile() && !alreadyCachedSpec.isSatisfiedBy(original)) {
                    return cache.useCache("Locate Jar file", new Factory<File>() {
                        public File create() {
                            return jarCache.getCachedJar(original, Factories.constant(cache.getBaseDir()));
                        }
                    });
                } else {
                    return original;
                }
            }
        }));
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    private class AlreadyCachedSpec implements Spec<File> {
        @Override
        public boolean isSatisfiedBy(File file) {
            String cacheRootDirName = cacheScopeMapping.getBaseDirectory(null, "dummy", CacheBuilder.VersionStrategy.SharedCache).getParent();
            return file.getAbsolutePath().startsWith(cacheRootDirName);
        }
    }
}
