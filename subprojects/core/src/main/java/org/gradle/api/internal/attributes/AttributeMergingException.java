/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.attributes.Attribute;

public class AttributeMergingException extends RuntimeException {
    private final Attribute<?> attribute;
    private final Object leftValue;
    private final Object rightValue;

    public AttributeMergingException(Attribute<?> attribute, Object leftValue, Object rightValue) {
        this(attribute, leftValue, rightValue, "An attribute named '" + attribute.getName() + "' of type '" + attribute.getType().getName() + "' already exists in this container");
    }

    public AttributeMergingException(Attribute<?> attribute, Object leftValue, Object rightValue, String message) {
        super(message);
        this.attribute = attribute;
        this.leftValue = leftValue;
        this.rightValue = rightValue;
    }

    public Attribute<?> getAttribute() {
        return attribute;
    }

    public Object getLeftValue() {
        return leftValue;
    }

    public Object getRightValue() {
        return rightValue;
    }
}
