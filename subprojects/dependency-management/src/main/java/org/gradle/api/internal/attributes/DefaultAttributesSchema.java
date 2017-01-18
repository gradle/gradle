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

package org.gradle.api.internal.attributes;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.ComponentAttributeMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultAttributesSchema implements AttributesSchemaInternal {

    private final ComponentAttributeMatcher componentAttributeMatcher;
    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = Maps.newHashMap();
    private final Map<Key, List<? extends HasAttributes>> matchesCache = Maps.newHashMap();

    public DefaultAttributesSchema(ComponentAttributeMatcher componentAttributeMatcher) {
        this.componentAttributeMatcher = componentAttributeMatcher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) {
        AttributeMatchingStrategy<?> strategy = strategies.get(attribute);
        if (strategy == null) {
            throw new IllegalArgumentException("Unable to find matching strategy for " + attribute);
        }
        return Cast.uncheckedCast(strategy);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute) {
        return attribute(attribute, null);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, Action<? super AttributeMatchingStrategy<T>> configureAction) {
        AttributeMatchingStrategy<T> strategy = Cast.uncheckedCast(strategies.get(attribute));
        if (strategy == null) {
            strategy = new DefaultAttributeMatchingStrategy<T>();
            strategies.put(attribute, strategy);
        }
        if (configureAction != null) {
            configureAction.execute(strategy);
        }
        return strategy;
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return strategies.keySet();
    }

    @Override
    public boolean hasAttribute(Attribute<?> key) {
        return strategies.containsKey(key);
    }

    @Override
    public List<? extends HasAttributes> getMatches(AttributesSchema producerAttributeSchema, List<HasAttributes> candidates, AttributeContainer consumer) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Key key = new Key(producerAttributeSchema, candidates, consumer);
        List<? extends HasAttributes> match = this.matchesCache.get(key);
        if (match == null) {
            match = componentAttributeMatcher.match(this, producerAttributeSchema, candidates, consumer);
            matchesCache.put(key, match);
        }
        return match;
    }

    @Override
    public boolean isMatching(AttributeContainer candidate, AttributeContainer target, boolean incompleteCandidate) {
        return componentAttributeMatcher.isMatching(this, candidate, target, incompleteCandidate);
    }

    private static class Key {
        final private AttributesSchema producerAttributeSchema;
        final private List<HasAttributes> candidates;
        final private AttributeContainer consumer;
        private final int hashCode;

        public Key(AttributesSchema producerAttributeSchema, List<HasAttributes> candidates, AttributeContainer consumer) {
            this.producerAttributeSchema = producerAttributeSchema;
            this.candidates = candidates;
            this.consumer = consumer;
            hashCode = doHashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return Objects.equal(producerAttributeSchema, key.producerAttributeSchema)
                && Objects.equal(consumer, key.consumer)
                && Objects.equal(candidates, key.candidates);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private int doHashCode() {
            return Objects.hashCode(producerAttributeSchema, candidates, consumer);
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("producerAttributeSchema", producerAttributeSchema)
                .add("candidates", candidates)
                .add("consumer", consumer)
                .toString();
        }
    }

}
