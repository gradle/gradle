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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.attributes.CompatibilityCheckResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
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
 * Default implementation of {@link AttributeSelectionSchema}, based off of a backing
 * {@link ImmutableAttributesSchema schema}.
 * <p>
 * This implementation should rarely be used on its own, and should almost always be
 * wrapped in a {@link CachingAttributeSelectionSchema}.
 */
public class DefaultAttributeSelectionSchema implements AttributeSelectionSchema {
    private final ImmutableAttributesSchema schema;

    public DefaultAttributeSelectionSchema(ImmutableAttributesSchema schema) {
        this.schema = schema;
    }

    @Override
    public boolean hasAttribute(Attribute<?> attribute) {
        return schema.getAttributes().contains(attribute);
    }

    @Override
    public <T> Set<T> disambiguate(Attribute<T> attribute, @Nullable T requested, Set<T> candidates) {
        ImmutableList<Action<? super MultipleCandidatesDetails<T>>> rules = schema.disambiguationRules(attribute);
        if (!rules.isEmpty()) {
            DefaultMultipleCandidateResult<T> result = new DefaultMultipleCandidateResult<>(requested, candidates);
            for (Action<? super MultipleCandidatesDetails<T>> rule : rules) {
                rule.execute(result);
                if (result.hasResult()) {
                    return result.getMatches();
                }
            }
        }

        if (requested != null && candidates.contains(requested)) {
            return Collections.singleton(requested);
        }

        return null;
    }

    @Override
    public <T> boolean matchValue(Attribute<T> attribute, T requested, T candidate) {
        if (requested.equals(candidate)) {
            return true;
        }

        ImmutableList<Action<? super CompatibilityCheckDetails<T>>> rules = schema.compatibilityRules(attribute);
        if (!rules.isEmpty()) {
            CompatibilityCheckResult<T> result = new DefaultCompatibilityCheckResult<>(requested, candidate);
            for (Action<? super CompatibilityCheckDetails<T>> rule : rules) {
                rule.execute(result);
                if (result.hasResult()) {
                    return result.isCompatible();
                }
            }
        }

        return false;
    }

    @Override
    public Attribute<?> getAttribute(String name) {
        return schema.getAttributeByName(name);
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
            // Some of these attributes might be weakly typed, e.g. coming as Strings from an
            // artifact repository. We always check whether the schema has a more strongly typed
            // version of an attribute and use that one instead to apply its disambiguation rules.
            extraAttributesArray[i] = tryRehydrate(extraAttributesArray[i]);
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
        if (schema.getAttributeDisambiguationPrecedence().isEmpty()) {
            // If no attribute precedence has been set anywhere, we can just iterate in order
            return new PrecedenceResult(IntStream.range(0, requested.size()).boxed().collect(Collectors.toList()));
        }

        // Populate requested attribute -> position in requested attribute list
        final Map<String, Integer> remaining = new LinkedHashMap<>();
        int position = 0;
        for (Attribute<?> requestedAttribute : requested) {
            remaining.put(requestedAttribute.getName(), position++);
        }

        List<Integer> sorted = new ArrayList<>(remaining.size());

        // Add attribute index to sorted in the order of precedence
        for (Attribute<?> preferredAttribute : schema.getAttributeDisambiguationPrecedence()) {
            if (requested.contains(preferredAttribute)) {
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
