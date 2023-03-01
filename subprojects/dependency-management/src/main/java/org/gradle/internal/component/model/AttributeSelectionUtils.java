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
package org.gradle.internal.component.model;

import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Iterator;
import java.util.Set;

public class AttributeSelectionUtils {
    public static Attribute<?>[] collectExtraAttributes(AttributeSelectionSchema schema, ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requested) {
        Set<Attribute<?>> extraAttributes = Sets.newLinkedHashSet();
        for (ImmutableAttributes attributes : candidateAttributeSets) {
            extraAttributes.addAll(attributes.keySet());
        }
        removeSameAttributes(requested, extraAttributes);
        Attribute<?>[] extraAttributesArray = extraAttributes.toArray(new Attribute<?>[0]);
        for (int i = 0; i < extraAttributesArray.length; i++) {
            Attribute<?> extraAttribute = extraAttributesArray[i];
            // Some of these attributes might be weakly typed, e.g. coming as Strings from an
            // artifact repository. We always check whether the schema has a more strongly typed
            // version of an attribute and use that one instead to apply its disambiguation rules.
            Attribute<?> schemaAttribute = schema.getAttribute(extraAttribute.getName());
            if (schemaAttribute != null) {
                extraAttributesArray[i] = schemaAttribute;
            }
        }
        return extraAttributesArray;
    }

    private static void removeSameAttributes(ImmutableAttributes requested, Set<Attribute<?>> extraAttributes) {
        for (Attribute<?> attribute : requested.keySet()) {
            Iterator<Attribute<?>> it = extraAttributes.iterator();
            while (it.hasNext()) {
                Attribute<?> next = it.next();
                if (next.getName().equals(attribute.getName())) {
                    it.remove();
                    break;
                }
            }
        }
    }
}
