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

import org.gradle.api.attributes.Attribute;
import org.gradle.internal.component.model.AttributeMatcher;

import javax.annotation.Nullable;

public interface AttributesSchemaInternal extends DescribableAttributesSchema {
    /**
     * Returns a matcher that uses the consumer rules from this schema, and the producer rules from the given schema.
     */
    AttributeMatcher withProducer(AttributesSchemaInternal producerSchema);

    /**
     * Returns a matcher that uses the rules from this schema, and assumes the producer has the same rules.
     */
    AttributeMatcher matcher();

    CompatibilityRule<Object> compatibilityRules(Attribute<?> attribute);

    DisambiguationRule<Object> disambiguationRules(Attribute<?> attribute);

    @Nullable
    Attribute<?> getAttributeByName(String name);
}
