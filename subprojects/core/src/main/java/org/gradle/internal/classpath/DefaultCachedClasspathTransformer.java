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
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.resource.local.ModificationTimeFileAccessTimeJournal;
import org.gradle.internal.resource.local.SingleDepthFileAccessTracker;
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

import static java.util.Collections.singleton;
import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer, Closeable {

    public static final String CACHE_KEY = "jars-3";
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final PersistentCache cache;
    private final Transformer<File, File> jarFileTransformer;

    public DefaultCachedClasspathTransformer(CacheRepository cacheRepository, JarCache jarCache, List<CachedJarFileStore> fileStores) {
        FileAccessTimeJournal fileAccessTimeJournal = new ModificationTimeFileAccessTimeJournal();
        this.cache = cacheRepository
            .cache(CACHE_KEY)
            .withDisplayName("jars")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withCleanup(new LeastRecentlyUsedCacheCleanup(
                new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
            .open();
        FileAccessTracker fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, cache.getBaseDir(), FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
        this.jarFileTransformer = new FileAccessTrackingJarFileTransformer(new CachedJarFileTransformer(jarCache, fileStores), fileAccessTracker);
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

    private class CachedJarFileTransformer implements Transformer<File, File> {
        private final JarCache jarCache;
        private final Factory<File> baseDir;
        private final List<String> prefixes;

        CachedJarFileTransformer(JarCache jarCache, List<CachedJarFileStore> fileStores) {
            this.jarCache = jarCache;
            baseDir = Factories.constant(cache.getBaseDir());
            prefixes = new ArrayList<String>(fileStores.size() + 1);
            prefixes.add(directoryPrefix(cache.getBaseDir()));
            for (CachedJarFileStore fileStore : fileStores) {
                for (File rootDir : fileStore.getFileStoreRoots()) {
                    prefixes.add(directoryPrefix(rootDir));
                }
            }
        }

        private String directoryPrefix(File dir) {
            return dir.getAbsolutePath() + File.separator;
        }

        @Override
        public File transform(final File original) {
            if (shouldUseFromCache(original)) {
                return cache.useCache(new Factory<File>() {
                    public File create() {
                        return jarCache.getCachedJar(original, baseDir);
                    }
                });
            }
            return original;
        }

        private boolean shouldUseFromCache(File original) {
            if (!original.isFile()) {
                return false;
            }
            String absolutePath = original.getAbsolutePath();
            for (String prefix : prefixes) {
                if (absolutePath.startsWith(prefix)) {
                    return false;
                }
            }
            return true;
        }
    }

    private class FileAccessTrackingJarFileTransformer implements Transformer<File, File> {

        private final Transformer<File, File> delegate;
        private final FileAccessTracker fileAccessTracker;

        FileAccessTrackingJarFileTransformer(Transformer<File, File> delegate, FileAccessTracker fileAccessTracker) {
            this.delegate = delegate;
            this.fileAccessTracker = fileAccessTracker;
        }

        @Override
        public File transform(File file) {
            File result = delegate.transform(file);
            fileAccessTracker.markAccessed(singleton(result));
            return result;
        }
    }
}
