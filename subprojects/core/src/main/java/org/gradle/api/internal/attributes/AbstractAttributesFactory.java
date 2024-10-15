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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.Cast;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Abstract base implementation of {@link AttributesFactory}, that memoizes {@link #fromMap(Map)}
 * conversions to improve performance.
 */
public abstract class AbstractAttributesFactory implements AttributesFactory {
    private final LoadingCache<Map<Attribute<?>, ?>, ImmutableAttributes> conversionCache  = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build(
            new CacheLoader<Map<Attribute<?>, ?>, ImmutableAttributes>() {
                @Override
                public ImmutableAttributes load(Map<Attribute<?>, ?> attributes) {
                    ImmutableAttributes result = ImmutableAttributes.EMPTY;
                    for (Map.Entry<Attribute<?>, ?> entry : attributes.entrySet()) {
                        /*
                            The order of the concatenation arguments here is important, as we have tests like
                            ConfigurationCacheDependencyResolutionIntegrationTest and ConfigurationCacheDependencyResolutionIntegrationTest
                            that rely on a particular order of failures when there are multiple invalid attribute type
                            conversions.  So even if it looks unnatural to list result second, this should remain.
                         */
                        result = concat(of(entry.getKey(), Cast.uncheckedNonnullCast(entry.getValue())), result);
                    }
                    return result;
                }
            });

    /**
     * Returns an attribute container that contains the values in the given map
     * of attribute to attribute value.
     * <p>
     * This method is meant to be the inverse of {@link AttributeContainerInternal#asMap()}.
     *
     * @param attributes the attribute values the result should contain
     * @return immutable instance containing only the specified attributes
     */
    @Override
    public ImmutableAttributes fromMap(Map<Attribute<?>, ?> attributes) {
        try {
            return conversionCache.get(attributes);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
