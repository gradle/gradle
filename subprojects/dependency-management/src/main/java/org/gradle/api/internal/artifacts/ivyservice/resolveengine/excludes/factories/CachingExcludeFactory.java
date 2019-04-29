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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;

import java.util.List;
import java.util.Map;

/**
 * This factory is responsible for caching merging queries. It delegates computations
 * to another factory, so if the delegate returns the same instances for the same
 * queries, caching will be faster.
 */
public class CachingExcludeFactory extends DelegatingExcludeFactory {
    private final Map<ExcludePair, ExcludeSpec> allOfPairCache = Maps.newConcurrentMap();
    private final Map<ExcludePair, ExcludeSpec> anyOfPairCache = Maps.newConcurrentMap();
    private final Map<ExcludeList, ExcludeSpec> allOfListCache = Maps.newConcurrentMap();
    private final Map<ExcludeList, ExcludeSpec> anyOfListCache = Maps.newConcurrentMap();

    public CachingExcludeFactory(ExcludeFactory delegate) {
        super(delegate);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return cachedAnyPair(one, two);
    }

    private ExcludeSpec cachedAnyPair(ExcludeSpec left, ExcludeSpec right) {
        return anyOfPairCache.computeIfAbsent(new ExcludePair(left, right), key -> delegate.anyOf(key.left, key.right));
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return cachedAllPair(one, two);
    }

    private ExcludeSpec cachedAllPair(ExcludeSpec left, ExcludeSpec right) {
        return allOfPairCache.computeIfAbsent(new ExcludePair(left, right), key -> delegate.allOf(key.left, key.right));
    }

    @Override
    public ExcludeSpec anyOf(List<ExcludeSpec> specs) {
        return anyOfListCache.computeIfAbsent(new ExcludeList(specs), key -> delegate.anyOf(key.specs));
    }

    @Override
    public ExcludeSpec allOf(List<ExcludeSpec> specs) {
        return allOfListCache.computeIfAbsent(new ExcludeList(specs), key -> delegate.allOf(key.specs));
    }

    /**
     * A special key which recognizes the fact union and intersection
     * are commutative.
     */
    private final static class ExcludePair {
        private final ExcludeSpec left;
        private final ExcludeSpec right;
        private final int hashCode;

        private ExcludePair(ExcludeSpec left, ExcludeSpec right) {
            int lhc = left.hashCode();
            int rhc = right.hashCode();
            this.hashCode = lhc ^ rhc; // must be symmetrical
            // Optimizes comparisons by making sure that the 2 elements of
            // the pair are "sorted" by hashcode ascending
            this.left = lhc<rhc ? left : right;
            this.right = lhc<rhc ? right : left;
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
    private static class ExcludeList {
        private final List<ExcludeSpec> specs;
        private final int size;
        private final int hashCode;

        private ExcludeList(List<ExcludeSpec> specs) {
            this.specs = specs;
            this.size = specs.size();
            this.hashCode = computeHashCode();
        }

        private int computeHashCode() {
            int hash = 0;
            for (ExcludeSpec spec : specs) {
                hash += spec.hashCode();
            }
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ExcludeList that = (ExcludeList) o;
            if (size != that.size) {
                return false;
            }
            return specs.containsAll(that.specs);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
