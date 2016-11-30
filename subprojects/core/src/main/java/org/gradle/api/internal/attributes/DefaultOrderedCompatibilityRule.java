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

import org.gradle.api.attributes.AttributeValue;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.OrderedCompatibilityRule;

public class DefaultOrderedCompatibilityRule<T extends Comparable<T>> implements OrderedCompatibilityRule<T> {
    private boolean reverse = false;

    @Override
    public OrderedCompatibilityRule<T> reverseOrder() {
        reverse = true;
        return this;
    }

    @Override
    public void checkCompatibility(CompatibilityCheckDetails<T> details) {
        AttributeValue<T> consumerValue = details.getConsumerValue();
        AttributeValue<T> producerValue = details.getProducerValue();
        if (consumerValue.isPresent() && producerValue.isPresent()) {
            T consumerPresent = consumerValue.get();
            T producerPresent = producerValue.get();
            int cmp = consumerPresent.compareTo(producerPresent);
            if (cmp == 0) {
                details.compatible();
            } else {
                boolean compatible = cmp < 0;
                if (reverse) {
                    compatible = !compatible;
                }
                if (compatible) {
                    details.compatible();
                } else {
                    details.incompatible();
                }
            }
        }
    }
}
