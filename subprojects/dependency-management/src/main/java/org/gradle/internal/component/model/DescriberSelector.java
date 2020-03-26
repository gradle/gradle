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

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ConsumerAttributeDescriber;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DescriberSelector {
    public static ConsumerAttributeDescriber selectDescriber(AttributeContainerInternal consumerAttributes, AttributesSchemaInternal consumerSchema) {
        List<ConsumerAttributeDescriber> consumerDescribers = consumerSchema.getConsumerDescribers();
        Set<Attribute<?>> consumerAttributeSet = consumerAttributes.keySet();
        for (ConsumerAttributeDescriber consumerDescriber : consumerDescribers) {
            if (consumerDescriber.getAttributes().equals(consumerAttributeSet)) {
                return new FallbackDescriber(consumerDescriber);
            }
        }
        return DefaultDescriber.INSTANCE;
    }

    private static class FallbackDescriber implements ConsumerAttributeDescriber {
        private final ConsumerAttributeDescriber delegate;

        private FallbackDescriber(ConsumerAttributeDescriber delegate) {
            this.delegate = delegate;
        }


        @Override
        public Set<Attribute<?>> getAttributes() {
            return delegate.getAttributes();
        }

        @Override
        public String describe(AttributeContainer attributes) {
            String description = delegate.describe(attributes);
            return description == null ? DefaultDescriber.INSTANCE.describe(attributes) : description;
        }
    }

    private static class DefaultDescriber implements ConsumerAttributeDescriber {
        private final static DefaultDescriber INSTANCE = new DefaultDescriber();

        @Override
        public Set<Attribute<?>> getAttributes() {
            return Collections.emptySet();
        }

        @Override
        public String describe(AttributeContainer attributes) {
            StringBuilder sb = new StringBuilder();
            for (Attribute<?> attribute : attributes.keySet()) {
                if (sb.length()>0) {
                    sb.append(", ");
                }
                sb.append("attribute '").append(attribute.getName()).append("' with value '").append(attributes.getAttribute(attribute) + "'");
            }
            return sb.toString();
        }
    }
}
