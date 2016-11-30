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

import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.CompatibilityRule;
import org.gradle.api.attributes.OrderedCompatibilityRule;
import org.gradle.api.attributes.OrderedDisambiguationRule;
import org.gradle.internal.Cast;

import java.util.Comparator;

public abstract class AttributeMatchingRules {
    private static final EqualityCompatibilityRule EQUALITY_RULE = new EqualityCompatibilityRule();
    private static final CompatibilityRule OPTIONAL_ON_PRODUCER = new CompatibilityRule() {
        @Override
        public void checkCompatibility(CompatibilityCheckDetails details) {
            if (details.getProducerValue().isMissing() || details.getProducerValue().isUnknown()) {
                details.compatible();
            }
        }
    };

    private static final CompatibilityRule OPTIONAL_ON_CONSUMER = new CompatibilityRule() {
        @Override
        public void checkCompatibility(CompatibilityCheckDetails details) {
            if (details.getConsumerValue().isMissing() || details.getConsumerValue().isUnknown()) {
                details.compatible();
            }
        }
    };

    public static <T> CompatibilityRule<T> equalityCompatibility() {
        return Cast.uncheckedCast(EQUALITY_RULE);
    }

    public static <T> OrderedCompatibilityRule<T> orderedCompatibility(Comparator<? super T> comparator) {
        return new DefaultOrderedCompatibilityRule<T>(comparator);
    }

    public static <T> OrderedDisambiguationRule<T> orderedDisambiguation(Comparator<? super T> comparator) {
        return new DefaultOrderedDisambiguationRule<T>(comparator);
    }

    public static <T> CompatibilityRule<T> optionalOnProducer() {
        return Cast.uncheckedCast(OPTIONAL_ON_PRODUCER);
    }

    public static <T> CompatibilityRule<T> optionalOnConsumer() {
        return Cast.uncheckedCast(OPTIONAL_ON_CONSUMER);
    }
}
