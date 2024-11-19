/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.attributes.matching;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.gradle.internal.model.InMemoryCacheFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * Caches results of a delegate {@link AttributeSelectionSchema}. Not all methods
 * are cached, as we only want to add caching to methods that have been proven
 * to be expensive.
 */
public class CachingAttributeSelectionSchema implements AttributeSelectionSchema {

    private final AttributeSelectionSchema delegate;

    private final InMemoryLoadingCache<ExtraAttributesKey, Attribute<?>[]> extraAttributesCache;
    private final InMemoryLoadingCache<MatchValueKey<?>, Boolean> matchValueCache;

    public CachingAttributeSelectionSchema(
        AttributeSelectionSchema delegate,
        InMemoryCacheFactory cacheFactory
    ) {
        this.delegate = delegate;

        this.extraAttributesCache = cacheFactory.create(this::doCollectExtraAttributes);
        this.matchValueCache = cacheFactory.create(this::doMatchValue);
    }

    @Override
    public boolean hasAttribute(Attribute<?> attribute) {
        return delegate.hasAttribute(attribute);
    }

    @Nullable
    @Override
    public <T> Set<T> disambiguate(Attribute<T> attribute, @Nullable T requested, Set<T> candidates) {
        return delegate.disambiguate(attribute, requested, candidates);
    }

    @Override
    public <T> boolean matchValue(Attribute<T> attribute, T requested, T candidate) {
        return matchValueCache.get(new MatchValueKey<>(attribute, requested, candidate));
    }

    private <T> boolean doMatchValue(MatchValueKey<T> key) {
        return delegate.matchValue(key.attribute, key.requested, key.candidate);
    }

    private static class MatchValueKey<T> {
        private final Attribute<T> attribute;
        private final T requested;
        private final T candidate;

        private final int hashCode;

        private MatchValueKey(Attribute<T> attribute, T requested, T candidate) {
            this.attribute = attribute;
            this.requested = requested;
            this.candidate = candidate;

            this.hashCode = computeHashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MatchValueKey<?> that = (MatchValueKey<?>) o;
            return attribute.equals(that.attribute) &&
                requested.equals(that.requested) &&
                candidate.equals(that.candidate);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private int computeHashCode() {
            int result = attribute.hashCode();
            result = 31 * result + requested.hashCode();
            result = 31 * result + candidate.hashCode();
            return result;
        }
    }

    @Override
    public Attribute<?> getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requested) {
        // TODO: Evaluate whether we still need this cache
        ExtraAttributesKey entry = new ExtraAttributesKey(candidateAttributeSets, requested);
        return extraAttributesCache.get(entry);
    }

    private Attribute<?>[] doCollectExtraAttributes(ExtraAttributesKey key) {
        return delegate.collectExtraAttributes(key.candidates, key.requested);
    }

    private static class ExtraAttributesKey {
        private final ImmutableAttributes[] candidates;
        private final ImmutableAttributes requested;
        private final int hashCode;

        private ExtraAttributesKey(ImmutableAttributes[] candidates, ImmutableAttributes requested) {
            this.candidates = candidates;
            this.requested = requested;
            this.hashCode = computeHashCode(candidates, requested);
        }

        private static int computeHashCode(ImmutableAttributes[] candidates, ImmutableAttributes requested) {
            int hash = requested.hashCode();
            for (ImmutableAttributes candidate : candidates) {
                hash = 31 * hash + candidate.hashCode();
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

            // We leverage identity here, as we intern ImmutableAttributes instances.
            // In some cases this may lead to false negatives e.g. for attribute sets created
            // in different orders. a->foo,b->bar and b->bar,a->foo will .equals each other but
            // will not == each other.

            ExtraAttributesKey that = (ExtraAttributesKey) o;
            if (requested != that.requested) {
                return false;
            }
            if (candidates.length != that.candidates.length) {
                return false;
            }
            for (int i = 0; i < candidates.length; i++) {
                if (candidates[i] != that.candidates[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    @Override
    public PrecedenceResult orderByPrecedence(Collection<Attribute<?>> requested) {
        return delegate.orderByPrecedence(requested);
    }
}
