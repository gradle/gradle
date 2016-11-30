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

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityRuleChain;
import org.gradle.api.attributes.DisambiguationRuleChain;
import org.gradle.internal.Cast;

import java.util.Map;
import java.util.Set;

public class DefaultAttributesSchema implements AttributesSchema {
    private static final Action<? super AttributeMatchingStrategy<String>> STRING_MATCHING = new Action<AttributeMatchingStrategy<String>>() {
        @Override
        public void execute(AttributeMatchingStrategy<String> strategy) {
            CompatibilityRuleChain<String> compatibilityRules = strategy.getCompatibilityRules();
            compatibilityRules.add(new EqualityCompatibilityRule<String>());
            compatibilityRules.eventuallyIncompatible();

            DisambiguationRuleChain<String> disambiguationRules = strategy.getDisambiguationRules();
            disambiguationRules.eventuallySelectAll();
        }
    };

    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = Maps.newHashMap();

    @Override
    @SuppressWarnings("unchecked")
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) {
        AttributeMatchingStrategy<?> strategy = strategies.get(attribute);
        if (strategy == null && String.class == attribute.getType()) {
            strategy = configureMatchingStrategy(attribute, (Action<? super AttributeMatchingStrategy<T>>) STRING_MATCHING);
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Unable to find matching strategy for " + attribute);
        }
        return Cast.uncheckedCast(strategy);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> configureMatchingStrategy(Attribute<T> attribute, Action<? super AttributeMatchingStrategy<T>> configureAction) {
        AttributeMatchingStrategy<T> strategy = Cast.uncheckedCast(strategies.get(attribute));
        if (strategy == null) {
            strategy = new DefaultAttributeMatchingStrategy<T>();
            strategies.put(attribute, strategy);
        }
        configureAction.execute(strategy);
        return strategy;
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return strategies.keySet();
    }

}
