/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import com.google.common.collect.Maps;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * This factory is responsible for caching merging queries. It delegates computations
 * to another factory, so if the delegate returns the same instances for the same
 * queries, caching will be faster.
 */
public class CachingExcludeFactory extends DelegatingExcludeFactory {
    private final MergeCaches caches;

    public CachingExcludeFactory(ExcludeFactory delegate, MergeCaches caches) {
        super(delegate);
        this.caches = caches;
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return cachedAnyPair(one, two);
    }

    private ExcludeSpec cachedAnyPair(ExcludeSpec left, ExcludeSpec right) {
        return caches.getAnyPair(ExcludePair.of(left, right), key -> delegate.anyOf(key.left, key.right));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return caches.getAllPair(ExcludePair.of(one, two), key -> delegate.allOf(key.left, key.right));
    }

    @Override
    public ExcludeSpec anyOf(Set<ExcludeSpec> specs) {
        return caches.getAnyOf(new ExcludesKey(specs), key -> delegate.anyOf(key.specs));
    }

    @Override
    public ExcludeSpec allOf(Set<ExcludeSpec> specs) {
        return caches.getAllOf(new ExcludesKey(specs), key -> delegate.allOf(key.specs));
    }

    /**
     * A special key which recognizes the fact union and intersection
     * are commutative.
     */
    private final static class ExcludePair {
        private final ExcludeSpec left;
        private final ExcludeSpec right;
        private final int hashCode;

        // Optimizes comparisons by making sure that the 2 elements of
        // the pair are "sorted" by hashcode ascending
        private static ExcludePair of(ExcludeSpec left, ExcludeSpec right) {
            if (left.hashCode() > right.hashCode()) {
                return new ExcludePair(right, left);
            }
            return new ExcludePair(left, right);
        }

        private ExcludePair(ExcludeSpec left, ExcludeSpec right) {
            this.left = left;
            this.right = right;
            this.hashCode = 31 * left.hashCode() + right.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ExcludePair that = (ExcludePair) o;

            return left.equals(that.left) && right.equals(that.right);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * A special exclude spec list key which recognizes
     * that union and intersection are commutative.
     */
    private static class ExcludesKey {
        private final Set<ExcludeSpec> specs;
        private final int size;
        private final int hashCode;

        private ExcludesKey(Set<ExcludeSpec> specs) {
            this.specs = specs;
            this.size = specs.size();
            this.hashCode = specs.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ExcludesKey that = (ExcludesKey) o;
            if (size != that.size) {
                return false;
            }
            return specs.equals(that.specs);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * A shareable backing cache for different caching exclude factories.
     * Synchronization is ad-hoc, since `computeIfAbsent` on a concurrent hash map
     * will not allow for recursion, which is the case for us whenever a cache is
     * found at different levels.
     */
    public static class MergeCaches {
        private final ConcurrentCache<ExcludePair, ExcludeSpec> allOfPairCache = ConcurrentCache.of();
        private final ConcurrentCache<ExcludePair, ExcludeSpec> anyOfPairCache = ConcurrentCache.of();
        private final ConcurrentCache<ExcludesKey, ExcludeSpec> allOfListCache = ConcurrentCache.of();
        private final ConcurrentCache<ExcludesKey, ExcludeSpec> anyOfListCache = ConcurrentCache.of();

        ExcludeSpec getAnyPair(ExcludePair pair, Function<ExcludePair, ExcludeSpec> onMiss) {
            return anyOfPairCache.computeIfAbsent(pair, onMiss);
        }

        ExcludeSpec getAllPair(ExcludePair pair, Function<ExcludePair, ExcludeSpec> onMiss) {
            return allOfPairCache.computeIfAbsent(pair, onMiss);
        }

        ExcludeSpec getAnyOf(ExcludesKey list, Function<ExcludesKey, ExcludeSpec> onMiss) {
            return anyOfListCache.computeIfAbsent(list, onMiss);
        }

        ExcludeSpec getAllOf(ExcludesKey list, Function<ExcludesKey, ExcludeSpec> onMiss) {
            return allOfListCache.computeIfAbsent(list, onMiss);
        }
    }

    private static class ConcurrentCache<K, V> {
        private final Map<K, V> backingMap = Maps.newHashMap();

        static <K, V> ConcurrentCache<K, V> of() {
            return new ConcurrentCache<>();
        }

        V computeIfAbsent(K key, Function<K, V> producer) {
            synchronized (backingMap) {
                V value = backingMap.get(key);
                if (value != null) {
                    return value;
                }
                value = producer.apply(key);
                backingMap.put(key, value);
                return value;
            }
        }
    }
}
