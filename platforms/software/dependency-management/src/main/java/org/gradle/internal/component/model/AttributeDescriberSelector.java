/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AttributeDescriberSelector {
    public static AttributeDescriber selectDescriber(AttributeContainerInternal consumerAttributes, List<AttributeDescriber> attributeDescribers) {
        Set<Attribute<?>> consumerAttributeSet = consumerAttributes.keySet();
        AttributeDescriber current = null;
        int maxSize = 0;
        for (AttributeDescriber describer : attributeDescribers) {
            int size = Sets.intersection(describer.getDescribableAttributes(), consumerAttributeSet).size();
            if (size > maxSize) {
                // Select the describer which handles the maximum number of attributes
                current = describer;
                maxSize = size;
            }
        }
        if (current != null) {
            return new FallbackDescriber(current);
        }
        return DefaultDescriber.INSTANCE;
    }

    private static class FallbackDescriber implements AttributeDescriber {
        private final AttributeDescriber delegate;

        private FallbackDescriber(AttributeDescriber delegate) {
            this.delegate = delegate;
        }


        @Override
        public ImmutableSet<Attribute<?>> getDescribableAttributes() {
            return delegate.getDescribableAttributes();
        }

        @Override
        public String describeAttributeSet(Map<Attribute<?>, ?> attributes) {
            String description = delegate.describeAttributeSet(attributes);
            return description == null ? DefaultDescriber.INSTANCE.describeAttributeSet(attributes) : description;
        }

        @Override
        public String describeMissingAttribute(Attribute<?> attribute, Object producerValue) {
            String description = delegate.describeMissingAttribute(attribute, producerValue);
            return description == null ? DefaultDescriber.INSTANCE.describeMissingAttribute(attribute, producerValue) : description;
        }

        @Override
        public String describeExtraAttribute(Attribute<?> attribute, Object producerValue) {
            String description = delegate.describeExtraAttribute(attribute, producerValue);
            return description == null ? DefaultDescriber.INSTANCE.describeExtraAttribute(attribute, producerValue) : description;
        }
    }

    private static class DefaultDescriber implements AttributeDescriber {
        private final static DefaultDescriber INSTANCE = new DefaultDescriber();

        @Override
        public ImmutableSet<Attribute<?>> getDescribableAttributes() {
            return ImmutableSet.of();
        }

        @Override
        public String describeAttributeSet(Map<Attribute<?>, ?> attributes) {
            StringBuilder sb = new StringBuilder();
            attributes.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getName()))
                .forEach(entry -> {
                    Attribute<?> attribute = entry.getKey();
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append("attribute '").append(attribute.getName()).append("' with value '").append(entry.getValue()).append("'");
                });
            return sb.toString();
        }

        @Override
        public String describeMissingAttribute(Attribute<?> attribute, Object consumerValue) {
            return attribute.getName() + " (required '" + consumerValue + "')";
        }

        @Override
        public String describeExtraAttribute(Attribute<?> attribute, Object producerValue) {
            return attribute.getName() + " '" + producerValue + "'";
        }
    }
}
