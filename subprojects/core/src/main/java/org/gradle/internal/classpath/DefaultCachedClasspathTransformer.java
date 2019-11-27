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
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheVersionMapping;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.resource.local.SingleDepthFileAccessTracker;
import org.gradle.internal.snapshot.WellKnownFileLocations;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import static org.gradle.cache.internal.CacheVersionMapping.introducedIn;

public class DefaultCachedClasspathTransformer implements CachedClasspathTransformer {

    public static final CacheVersionMapping CACHE_VERSION_MAPPING = introducedIn("3.1-rc-1").incrementedIn("3.2-rc-1").incrementedIn("3.5-rc-1").build();
    public static final String CACHE_NAME = "jars";
    public static final String CACHE_KEY = CACHE_NAME + "-" + CACHE_VERSION_MAPPING.getLatestVersion();
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final PersistentCache cache;
    private final Transformer<File, File> jarFileTransformer;

    public DefaultCachedClasspathTransformer(
        ClasspathTransformerCache classpathTransformerCache,
        FileAccessTimeJournal fileAccessTimeJournal,
        JarCache jarCache,
        WellKnownFileLocations wellKnownFileLocations
    ) {
        this.cache = classpathTransformerCache.getCache();
        FileAccessTracker fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, cache.getBaseDir(), FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
        this.jarFileTransformer = new FileAccessTrackingJarFileTransformer(new CachedJarFileTransformer(jarCache, wellKnownFileLocations), fileAccessTracker);
    }

    @Override
    public ClassPath transform(ClassPath classPath) {
        return DefaultClassPath.of(CollectionUtils.collect(classPath.getAsFiles(), jarFileTransformer));
    }

    @Override
    public Collection<URL> transform(Collection<URL> urls) {
        return CollectionUtils.collect(urls, url -> {
            if (url.getProtocol().equals("file")) {
                try {
                    return jarFileTransformer.transform(new File(url.toURI())).toURI().toURL();
                } catch (URISyntaxException | MalformedURLException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            } else {
                return url;
            }
        });
    }

    private class CachedJarFileTransformer implements Transformer<File, File> {
        private final JarCache jarCache;
        private final WellKnownFileLocations wellKnownFileLocations;
        private final Factory<File> baseDir;

        CachedJarFileTransformer(JarCache jarCache, WellKnownFileLocations wellKnownFileLocations) {
            this.jarCache = jarCache;
            this.wellKnownFileLocations = wellKnownFileLocations;
            this.baseDir = Factories.constant(cache.getBaseDir());
        }

        @Override
        public File transform(final File original) {
            if (shouldUseFromCache(original)) {
                return cache.useCache(() -> jarCache.getCachedJar(original, baseDir));
            }
            return original;
        }

        private boolean shouldUseFromCache(File original) {
            if (!original.isFile()) {
                return false;
            }
            String absolutePath = original.getAbsolutePath();
            return !wellKnownFileLocations.isImmutable(absolutePath);
        }
    }

    private static class FileAccessTrackingJarFileTransformer implements Transformer<File, File> {

        private final Transformer<File, File> delegate;
        private final FileAccessTracker fileAccessTracker;

        FileAccessTrackingJarFileTransformer(Transformer<File, File> delegate, FileAccessTracker fileAccessTracker) {
            this.delegate = delegate;
            this.fileAccessTracker = fileAccessTracker;
        }

        @Override
        public File transform(File file) {
            File result = delegate.transform(file);
            fileAccessTracker.markAccessed(result);
            return result;
        }
    }
}
