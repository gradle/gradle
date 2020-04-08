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
package org.gradle.internal.component;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.Map;
import java.util.Set;

class StyledDescriber implements AttributeDescriber {

    private final AttributeDescriber delegate;

    StyledDescriber(AttributeDescriber delegate) {
        this.delegate = delegate;
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public String describeAttributeSet(Map<Attribute<?>, ?> attributes) {
        return StyledException.style(StyledTextOutput.Style.Header, delegate.describeAttributeSet(attributes));
    }

    @Override
    public String describeMissingAttribute(Attribute<?> attribute, Object consumerValue) {
        return StyledException.style(StyledTextOutput.Style.Info, delegate.describeMissingAttribute(attribute, consumerValue));
    }

    @Override
    public String describeExtraAttribute(Attribute<?> attribute, Object producerValue) {
        return StyledException.style(StyledTextOutput.Style.Info, delegate.describeExtraAttribute(attribute, producerValue));
    }

}
