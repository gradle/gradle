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

package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * De-duplicates FilePath and Path instances referenced in the built IdeaModel and the IdeaProjects contained in it
 *
 * Uses the constructor arguments as the key for each instance since {@link Path} has custom {@link Path#equals(Object)} and
 * {@link Path#hashCode()} implementation that only uses the canonicalUrl field.
 */
public class PathInterner {
    final Cache<FilePathCacheKey, FilePath> filePathCache;
    final Cache<PathCacheKey, Path> pathCache;

    public PathInterner() {
        this(20000);
    }

    public PathInterner(int cacheMaxSize) {
        filePathCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
        pathCache = CacheBuilder.newBuilder().maximumSize(cacheMaxSize).build();
    }

    FilePath createFilePath(final File file, final String url, final String canonicalUrl, final String relPath) {
        try {
            return filePathCache.get(new FilePathCacheKey(file, url, canonicalUrl, relPath), new Callable<FilePath>() {
                @Override
                public FilePath call() throws Exception {
                    return new FilePath(file, url, canonicalUrl, relPath);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    Path createPath(final String url, final String expandedUrl, final String relPath) {
        try {
            return pathCache.get(new PathCacheKey(url, expandedUrl, relPath), new Callable<Path>() {
                @Override
                public Path call() throws Exception {
                    return new Path(url, expandedUrl, relPath);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static class FilePathCacheKey {
        private final File file;
        private final String url;
        private final String canonicalUrl;
        private final String relPath;

        FilePathCacheKey(File file, String url, String canonicalUrl, String relPath) {
            this.file = file;
            this.url = url;
            this.canonicalUrl = canonicalUrl;
            this.relPath = relPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FilePathCacheKey that = (FilePathCacheKey) o;

            return Objects.equal(this.file, that.file)
                    && Objects.equal(this.url, that.url)
                    && Objects.equal(this.canonicalUrl, that.canonicalUrl)
                    && Objects.equal(this.relPath, that.relPath);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(file, url, canonicalUrl, relPath);
        }
    }

    private static class PathCacheKey {
        private final String url;
        private final String expandedUrl;
        private final String relPath;

        PathCacheKey(String url, String expandedUrl, String relPath) {
            this.url = url;
            this.expandedUrl = expandedUrl;
            this.relPath = relPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PathCacheKey that = (PathCacheKey) o;

            return Objects.equal(this.url, that.url)
                    && Objects.equal(this.expandedUrl, that.expandedUrl)
                    && Objects.equal(this.relPath, that.relPath);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(url, expandedUrl, relPath);
        }
    }

}