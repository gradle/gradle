/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks.util.internal;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.cache.internal.HeapProportionalCacheSizer;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class CachingPatternSpecFactory extends PatternSpecFactory {
    private static final int RESULTS_CACHE_MAX_SIZE = 1200000;
    private static final int INSTANCES_MAX_SIZE = 30000;
    private final Cache<CacheKey, Boolean> specResultCache;
    private final Cache<SpecKey, Spec> specInstanceCache;

    public CachingPatternSpecFactory() {
        HeapProportionalCacheSizer cacheSizer = new HeapProportionalCacheSizer();
        specResultCache = CacheBuilder.newBuilder().maximumSize(cacheSizer.scaleCacheSize(RESULTS_CACHE_MAX_SIZE)).build();
        specInstanceCache = CacheBuilder.newBuilder().maximumSize(cacheSizer.scaleCacheSize(INSTANCES_MAX_SIZE)).build();
    }

    @Override
    protected Spec<FileTreeElement> createSpec(final Collection<String> patterns, final boolean include, final boolean caseSensitive) {
        final SpecKey key = new SpecKey(ImmutableList.copyOf(patterns), include, caseSensitive);
        try {
            return Cast.uncheckedCast(specInstanceCache.get(key, new Callable<Spec<FileTreeElement>>() {
                @Override
                public Spec<FileTreeElement> call() throws Exception {
                    Spec<FileTreeElement> spec = CachingPatternSpecFactory.super.createSpec(patterns, include, caseSensitive);
                    return new CachingSpec(key, spec);
                }
            }));
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private class CachingSpec implements Spec<FileTreeElement> {
        private final SpecKey key;
        private final Spec<FileTreeElement> spec;

        CachingSpec(SpecKey key, Spec<FileTreeElement> spec) {
            this.key = key;
            this.spec = spec;
        }

        @Override
        public boolean isSatisfiedBy(final FileTreeElement element) {
            CacheKey cacheKey = new CacheKey(element.getRelativePath(), key);
            try {
                return specResultCache.get(cacheKey, new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return spec.isSatisfiedBy(element);
                    }
                });
            } catch (ExecutionException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("key", key)
                .add("spec", spec)
                .toString();
        }
    }

    private static class CacheKey {
        private final RelativePath relativePath;
        private final SpecKey specKey;
        private final int hashCode;


        private CacheKey(RelativePath relativePath, SpecKey specKey) {
            this.relativePath = relativePath;
            this.specKey = specKey;
            this.hashCode = Objects.hashCode(relativePath, specKey);
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey that = (CacheKey) o;

            return Objects.equal(this.relativePath, that.relativePath)
                && Objects.equal(this.specKey, that.specKey);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("relativePath", relativePath)
                .add("specKey", specKey)
                .toString();
        }
    }

    private static class SpecKey {
        private final ImmutableList<String> patterns;
        private final boolean include;
        private final boolean caseSensitive;
        private final int hashCode;

        private SpecKey(ImmutableList<String> patterns, boolean include, boolean caseSensitive) {
            this.patterns = patterns;
            this.include = include;
            this.caseSensitive = caseSensitive;
            this.hashCode = Objects.hashCode(patterns, include, caseSensitive);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SpecKey that = (SpecKey) o;

            return Objects.equal(this.patterns, that.patterns)
                && Objects.equal(this.include, that.include)
                && Objects.equal(this.caseSensitive, that.caseSensitive);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("patterns", patterns)
                .add("include", include)
                .add("caseSensitive", caseSensitive)
                .toString();
        }
    }
}
