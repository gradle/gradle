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

import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.internal.attributes.matching.CachingAttributeSelectionSchema;
import org.gradle.api.internal.attributes.matching.DefaultAttributeMatcher;
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultAttributesSchema implements AttributesSchemaInternal {
    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;

    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = new HashMap<>();
    private final Map<String, Attribute<?>> attributesByName = new HashMap<>();
    private final Set<Attribute<?>> precedence = new LinkedHashSet<>();

    private final Map<AttributesSchemaInternal, AttributeMatcher> matcherCache = new ConcurrentHashMap<>();

    public DefaultAttributesSchema(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory) {
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
    }

    @Override
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
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, @Nullable Action<? super AttributeMatchingStrategy<T>> configureAction) {
        AttributeMatchingStrategy<T> strategy = Cast.uncheckedCast(strategies.get(attribute));
        if (strategy == null) {
            strategy = Cast.uncheckedCast(instantiatorFactory.decorateLenient().newInstance(DefaultAttributeMatchingStrategy.class, instantiatorFactory, isolatableFactory));
            strategies.put(attribute, strategy);
            attributesByName.put(attribute.getName(), attribute);
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
    public AttributeMatcher withProducer(AttributesSchemaInternal producerSchema) {
        return matcherCache.computeIfAbsent(producerSchema, key ->
            new DefaultAttributeMatcher(
                new CachingAttributeSelectionSchema(
                    new DefaultAttributeSelectionSchema(this, producerSchema)
                )
            )
        );
    }

    @Override
    public AttributeMatcher matcher() {
        return withProducer(EmptySchema.INSTANCE);
    }

    @Override
    public CompatibilityRule<Object> compatibilityRules(Attribute<?> attribute) {
        AttributeMatchingStrategy<?> matchingStrategy = strategies.get(attribute);
        if (matchingStrategy != null) {
            return Cast.uncheckedCast(matchingStrategy.getCompatibilityRules());
        }
        return EmptySchema.INSTANCE.compatibilityRules(attribute);
    }

    @Override
    public DisambiguationRule<Object> disambiguationRules(Attribute<?> attribute) {
        AttributeMatchingStrategy<?> matchingStrategy = strategies.get(attribute);
        if (matchingStrategy != null) {
            return Cast.uncheckedCast(matchingStrategy.getDisambiguationRules());
        }
        return EmptySchema.INSTANCE.disambiguationRules(attribute);
    }

    @Override
    public void attributeDisambiguationPrecedence(Attribute<?>... attributes) {
        for (Attribute<?> attribute : attributes) {
            if (!precedence.add(attribute)) {
                throw new IllegalArgumentException(String.format("Attribute '%s' precedence has already been set.", attribute.getName()));
            }
        }
    }

    @Override
    public void setAttributeDisambiguationPrecedence(List<Attribute<?>> attributes) {
        precedence.clear();
        attributeDisambiguationPrecedence(attributes.toArray(new Attribute<?>[0]));
    }

    @Override
    public List<Attribute<?>> getAttributeDisambiguationPrecedence() {
        return Collections.unmodifiableList(new ArrayList<>(precedence));
    }

    @Nullable
    @Override
    public Attribute<?> getAttributeByName(String name) {
        return attributesByName.get(name);
    }

}
