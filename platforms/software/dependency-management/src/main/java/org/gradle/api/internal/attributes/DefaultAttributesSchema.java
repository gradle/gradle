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
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultAttributesSchema implements AttributesSchemaInternal {
    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;

    private final Map<Attribute<?>, DefaultAttributeMatchingStrategy<?>> strategies = new HashMap<>();
    private final Set<Attribute<?>> precedence = new LinkedHashSet<>();

    @Inject
    public DefaultAttributesSchema(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory) {
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
    }

    // region public API

    @Override
    @SuppressWarnings("unchecked")
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) {
        AttributeMatchingStrategy<T> strategy = (DefaultAttributeMatchingStrategy<T>) strategies.get(attribute);
        if (strategy == null) {
            throw new IllegalArgumentException("Unable to find matching strategy for " + attribute);
        }
        return strategy;
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute) {
        return attribute(attribute, null);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, @Nullable Action<? super AttributeMatchingStrategy<T>> configureAction) {
        AttributeMatchingStrategy<T> strategy = getStrategy(attribute);
        if (configureAction != null) {
            configureAction.execute(strategy);
        }
        return strategy;
    }

    @SuppressWarnings("unchecked")
    private <T> AttributeMatchingStrategy<T> getStrategy(Attribute<T> attribute) {
        DefaultAttributeMatchingStrategy<T> strategy = (DefaultAttributeMatchingStrategy<T>) strategies.get(attribute);
        if (strategy == null) {
            strategy = instantiatorFactory.decorateLenient().newInstance(DefaultAttributeMatchingStrategy.class, instantiatorFactory, isolatableFactory);
            strategies.put(attribute, strategy);
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

    // endregion

    @Override
    public Map<Attribute<?>, DefaultAttributeMatchingStrategy<?>> getStrategies() {
        return Collections.unmodifiableMap(strategies);
    }


    @Override
    public Set<Attribute<?>> getAttributePrecedence() {
        return Collections.unmodifiableSet(precedence);
    }

}
