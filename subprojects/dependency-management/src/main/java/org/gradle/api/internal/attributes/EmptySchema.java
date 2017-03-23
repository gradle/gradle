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

package org.gradle.api.internal.attributes;

import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeSelectionSchema;

import java.util.List;
import java.util.Set;

public class EmptySchema implements AttributesSchemaInternal {
    public static final EmptySchema INSTANCE = new EmptySchema();

    @Override
    public AttributeMatcher ignoreAdditionalProducerAttributes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttributeMatcher ignoreAdditionalConsumerAttributes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends HasAttributes> List<T> getMatches(AttributeSelectionSchema producerAttributeSchema, List<T> candidates, AttributeContainer consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DisambiguationRuleChainInternal<Object> getDisambiguationRules(Attribute<?> attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompatibilityRuleChainInternal<Object> getCompatibilityRules(Attribute<?> attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, Action<? super AttributeMatchingStrategy<T>> configureAction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasAttribute(Attribute<?> key) {
        throw new UnsupportedOperationException();
    }
}
