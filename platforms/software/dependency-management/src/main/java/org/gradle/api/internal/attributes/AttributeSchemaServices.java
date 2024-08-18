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

package org.gradle.api.internal.attributes;

import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.internal.attributes.matching.CachingAttributeSelectionSchema;
import org.gradle.api.internal.attributes.matching.DefaultAttributeMatcher;
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A collection of services for creating and working with immutable attribute schemas.
 */
@ServiceScope(Scope.BuildSession.class)
public class AttributeSchemaServices {

    private final ImmutableAttributesFactory attributesFactory;
    private final ImmutableAttributesSchemaFactory attributesSchemaFactory;

    private final ConcurrentIdentityCache<ImmutableAttributesSchema, AttributeMatcher> matchers = new ConcurrentIdentityCache<>();
    private final Map<AttributeMatcher, ConsumerProvidedVariantFinder> transformSelectors = new ConcurrentHashMap<>();

    @Inject
    public AttributeSchemaServices(
        ImmutableAttributesFactory attributesFactory,
        ImmutableAttributesSchemaFactory attributesSchemaFactory
    ) {
        this.attributesFactory = attributesFactory;
        this.attributesSchemaFactory = attributesSchemaFactory;
    }

    /**
     * Get a factory that can produce immutable attribute schemas from mutable ones.
     */
    public ImmutableAttributesSchemaFactory getSchemaFactory() {
        return attributesSchemaFactory;
    }

    /**
     * Given a consumer and producer attribute schema, returns a matcher that can
     * be used to match attributes from the two schemas.
     * <p>
     * Returned matchers are cached and reused for the same input schemas.
     */
    public AttributeMatcher getMatcher(ImmutableAttributesSchema consumer, ImmutableAttributesSchema producer) {
        ImmutableAttributesSchema merged = attributesSchemaFactory.concat(consumer, producer);
        return matchers.computeIfAbsent(merged, key ->
            new DefaultAttributeMatcher(
                new CachingAttributeSelectionSchema(
                    new DefaultAttributeSelectionSchema(key)
                )
            )
        );
    }

    /**
     * Given an attribute matcher, return a selector that can determine artifact transform chains
     * for a given set of request attributes, source variants, and registered transforms.
     */
    public ConsumerProvidedVariantFinder getTransformSelector(AttributeMatcher matcher) {
        return transformSelectors.computeIfAbsent(matcher, m ->
            new ConsumerProvidedVariantFinder(m, attributesFactory)
        );
    }

}
