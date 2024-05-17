/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;

import java.util.Arrays;
import java.util.Map;

/**
 * Converts attributes to a stringy map preserving the order.
 */
public class AttributesToMapConverter {

    private AttributesToMapConverter() {}

    /**
     * Converts attributes to a stringy map preserving the order.
     */
    public static Map<String, String> convertToMap(AttributeContainer attributes) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Attribute<?> attribute : attributes.keySet()) {
            String strValue = getAttributeValueAsString(attributes, attribute);
            builder.put(attribute.getName(), strValue);
        }
        return builder.build();
    }

    private static String getAttributeValueAsString(AttributeContainer attributeContainer, Attribute<?> attribute) {
        // We use the same algorithm that Gradle uses when desugaring these on the build op, so that we don't end up
        // with unexpectedly different values due to arrays or Named objects being converted to Strings differently.
        // See LazyDesugaringAttributeContainer in the gradle/gradle codebase.
        Object attributeValue = attributeContainer.getAttribute(attribute);
        if (attributeValue == null) {
            throw new IllegalStateException("No attribute value for " + attribute);
        }

        if (attributeValue instanceof Named) {
            return ((Named) attributeValue).getName();
        } else if (attributeValue instanceof Object[]) {
            // don't bother trying to handle primitive arrays specially
            return Arrays.toString((Object[]) attributeValue);
        } else {
            return attributeValue.toString();
        }
    }

}
