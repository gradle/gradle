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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.JarCache;
import org.gradle.util.CollectionUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {
    private final PersistentCache cache;
    private final Transformer<File, File> jarFileTransformer;

    public DefaultCachedClasspathTransformer(CacheRepository cacheRepository, JarCache jarCache, List<CachedJarFileStore> fileStores) {
        this.cache = cacheRepository
            .cache("jars-2")
            .withDisplayName("jars")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .open();
        this.jarFileTransformer = new CachedJarFileTransformer(jarCache, cache, fileStores);
    }

    @Override
    public ClassPath transform(ClassPath classPath) {
        return DefaultClassPath.of(CollectionUtils.collect(classPath.getAsFiles(), jarFileTransformer));
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls) {
        return CollectionUtils.collect(urls, new Transformer<URL, URL>() {
            @Override
            public URL transform(URL url) {
                if (url.getProtocol().equals("file")) {
                    try {
                        return jarFileTransformer.transform(new File(url.toURI())).toURI().toURL();
                    } catch (URISyntaxException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    } catch (MalformedURLException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                } else {
                    return url;
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }

    private static class CachedJarFileTransformer implements Transformer<File, File> {
        private final JarCache jarCache;
        private final PersistentCache cache;
        private final List<String> prefixes;

        CachedJarFileTransformer(JarCache jarCache, PersistentCache cache, List<CachedJarFileStore> fileStores) {
            this.jarCache = jarCache;
            this.cache = cache;
            prefixes = new ArrayList<String>(fileStores.size() + 1);
            prefixes.add(cache.getBaseDir().getAbsolutePath() + File.separator);
            for (CachedJarFileStore fileStore : fileStores) {
                prefixes.add(fileStore.getJarFileStoreDirectory().getAbsolutePath() + File.separator);
            }
        }

        @Override
        public File transform(final File original) {
            if (moveToCache(original)) {
                return cache.useCache(new Factory<File>() {
                    public File create() {
                        return jarCache.getCachedJar(original, Factories.constant(cache.getBaseDir()));
                    }
                });
            } else {
                return original;
            }
        }

        private boolean moveToCache(File original) {
            if (!original.isFile()) {
                return false;
            }
            for (String prefix : prefixes) {
                if (original.getAbsolutePath().startsWith(prefix)) {
                    return false;
                }
            }
            return true;
        }
    }
}
