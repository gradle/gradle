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

import com.google.common.base.Objects;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.CompatibilityCheckResult;
import org.gradle.api.internal.attributes.CompatibilityRule;
import org.gradle.api.internal.attributes.DisambiguationRule;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult;
import org.gradle.internal.component.model.DefaultMultipleCandidateResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Default implementation of {@link AttributeSelectionSchema}.
 */
public class DefaultAttributeSelectionSchema implements AttributeSelectionSchema {
    private final AttributesSchemaInternal consumerSchema;
    private final AttributesSchemaInternal producerSchema;

    public DefaultAttributeSelectionSchema(AttributesSchemaInternal consumerSchema, AttributesSchemaInternal producerSchema) {
        this.consumerSchema = consumerSchema;
        this.producerSchema = producerSchema;
    }

    @Override
    public boolean hasAttribute(Attribute<?> attribute) {
        return consumerSchema.getAttributes().contains(attribute) || producerSchema.getAttributes().contains(attribute);
    }

    @Override
    public Set<Object> disambiguate(Attribute<?> attribute, @Nullable Object requested, Set<Object> candidates) {
        DefaultMultipleCandidateResult<Object> result = null;

        DisambiguationRule<Object> rules = consumerSchema.disambiguationRules(attribute);
        if (rules.doesSomething()) {
            result = new DefaultMultipleCandidateResult<>(requested, candidates);
            rules.execute(result);
            if (result.hasResult()) {
                return result.getMatches();
            }
        }

        rules = producerSchema.disambiguationRules(attribute);
        if (rules.doesSomething()) {
            if (result == null) {
                result = new DefaultMultipleCandidateResult<>(requested, candidates);
            }
            rules.execute(result);
            if (result.hasResult()) {
                return result.getMatches();
            }
        }

        if (requested != null && candidates.contains(requested)) {
            return Collections.singleton(requested);
        }

        return null;
    }

    @Override
    public boolean matchValue(Attribute<?> attribute, Object requested, Object candidate) {
        if (requested.equals(candidate)) {
            return true;
        }

        CompatibilityCheckResult<Object> result = null;

        CompatibilityRule<Object> rules = consumerSchema.compatibilityRules(attribute);
        if (rules.doesSomething()) {
            result = new DefaultCompatibilityCheckResult<>(requested, candidate);
            rules.execute(result);
            if (result.hasResult()) {
                return result.isCompatible();
            }
        }

        rules = producerSchema.compatibilityRules(attribute);
        if (rules.doesSomething()) {
            if (result == null) {
                result = new DefaultCompatibilityCheckResult<>(requested, candidate);
            }
            rules.execute(result);
            if (result.hasResult()) {
                return result.isCompatible();
            }
        }

        return false;
    }

    @Override
    public Attribute<?> getAttribute(String name) {
        Attribute<?> attribute = consumerSchema.getAttributeByName(name);
        if (attribute != null) {
            return attribute;
        }
        return producerSchema.getAttributeByName(name);
    }

    @Override
    public Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requested) {
        Set<Attribute<?>> extraAttributes = new LinkedHashSet<>();
        for (ImmutableAttributes attributes : candidateAttributeSets) {
            extraAttributes.addAll(attributes.keySet());
        }
        removeSameAttributes(requested, extraAttributes);
        Attribute<?>[] extraAttributesArray = extraAttributes.toArray(new Attribute<?>[0]);
        for (int i = 0; i < extraAttributesArray.length; i++) {
            Attribute<?> extraAttribute = extraAttributesArray[i];
            // Some of these attributes might be weakly typed, e.g. coming as Strings from an
            // artifact repository. We always check whether the schema has a more strongly typed
            // version of an attribute and use that one instead to apply its disambiguation rules.
            Attribute<?> schemaAttribute = getAttribute(extraAttribute.getName());
            if (schemaAttribute != null) {
                extraAttributesArray[i] = schemaAttribute;
            }
        }
        return extraAttributesArray;
    }

    private static void removeSameAttributes(ImmutableAttributes requested, Set<Attribute<?>> extraAttributes) {
        for (Attribute<?> attribute : requested.keySet()) {
            Iterator<Attribute<?>> it = extraAttributes.iterator();
            while (it.hasNext()) {
                Attribute<?> next = it.next();
                if (next.getName().equals(attribute.getName())) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public PrecedenceResult orderByPrecedence(Collection<Attribute<?>> requested) {
        if (consumerSchema.getAttributeDisambiguationPrecedence().isEmpty() && producerSchema.getAttributeDisambiguationPrecedence().isEmpty()) {
            // If no attribute precedence has been set anywhere, we can just iterate in order
            return new PrecedenceResult(IntStream.range(0, requested.size()).boxed().collect(Collectors.toList()));
        } else {
            // Populate requested attribute -> position in requested attribute list
            final Map<String, Integer> remaining = new LinkedHashMap<>();
            int position = 0;
            for (Attribute<?> requestedAttribute : requested) {
                remaining.put(requestedAttribute.getName(), position++);
            }
            List<Integer> sorted = new ArrayList<>(remaining.size());

            // Add attribute index to sorted in the order of precedence by the consumer
            for (Attribute<?> preferredAttribute : consumerSchema.getAttributeDisambiguationPrecedence()) {
                if (requested.contains(preferredAttribute)) {
                    sorted.add(remaining.remove(preferredAttribute.getName()));
                }
            }
            // Add attribute index to sorted in the order of precedence by the producer
            for (Attribute<?> preferredAttribute : producerSchema.getAttributeDisambiguationPrecedence()) {
                if (remaining.containsKey(preferredAttribute.getName()) && requested.contains(preferredAttribute)) {
                    sorted.add(remaining.remove(preferredAttribute.getName()));
                }
            }
            // If nothing was sorted, there were no attributes in the request that matched any attribute precedences
            if (sorted.isEmpty()) {
                // Iterate in order
                return new PrecedenceResult(remaining.values());
            } else {
                // sorted now contains any requested attribute indices in the order they appear in
                // the consumer and producer's attribute precedences
                return new PrecedenceResult(sorted, remaining.values());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultAttributeSelectionSchema that = (DefaultAttributeSelectionSchema) o;
        return consumerSchema.equals(that.consumerSchema) && producerSchema.equals(that.producerSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(consumerSchema, producerSchema);
    }
}
