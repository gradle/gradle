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
import org.gradle.api.Transformer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;

import java.util.Map;
import java.util.Set;

public class DefaultAttributesSchema implements AttributesSchema {
    private final Map<Attribute<?>, AttributeMatchingStrategy<?>> strategies = Maps.newHashMap();

    @Override
    public <T> void setMatchingStrategy(Attribute<T> attribute, AttributeMatchingStrategy<T> strategy) {
        strategies.put(attribute, strategy);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) {
        AttributeMatchingStrategy<?> strategy = strategies.get(attribute);
        if (strategy == null && String.class == attribute.getType()) {
            strategy = StrictMatchingStrategy.INSTANCE;
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Unable to find matching strategy for " + attribute);
        }
        return Cast.uncheckedCast(strategy);
    }

    @Override
    public <T> void matchStrictly(Attribute<T> attribute) {
        setMatchingStrategy(attribute, StrictMatchingStrategy.<T>get());
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return strategies.keySet();
    }

    private static class StrictMatchingStrategy<T> implements AttributeMatchingStrategy<T> {
        private static final StrictMatchingStrategy<?> INSTANCE = new StrictMatchingStrategy<Object>();

        public static <T> StrictMatchingStrategy<T> get() {
            return Cast.uncheckedCast(INSTANCE);
        }

        @Override
        public void checkCompatibility(final CompatibilityCheckDetails<T> details) {
            details.getConsumerValue().whenPresent(new Transformer<Void, T>() {
                @Override
                public Void transform(final T consumerValue) {
                    details.getProducerValue().whenPresent(new Transformer<Void, T>() {
                        @Override
                        public Void transform(T producerValue) {
                            if (consumerValue.equals(producerValue)) {
                                details.compatible();
                            } else {
                                details.incompatible();
                            }
                            return null;
                        }
                    }).getOrElse(new Factory<Void>() {
                        @Override
                        public Void create() {
                            details.incompatible();
                            return null;
                        }
                    });
                    return null;
                }
            }).getOrElse(new Factory<Void>() {
                @Override
                public Void create() {
                    details.incompatible();
                    return null;
                }
            });
        }

        @Override
        public <K> void selectClosestMatch(MultipleCandidatesDetails<T, K> details) {
            for (K candidate : details.getCandidateValues().keySet()) {
                details.closestMatch(candidate);
            }
        }
    }
}
