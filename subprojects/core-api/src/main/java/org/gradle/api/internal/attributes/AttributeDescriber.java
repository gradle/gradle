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
package org.gradle.api.internal.attributes;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.Attribute;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface AttributeDescriber {
    /**
     * Returns the set of attributes that this describer can describe.
     *
     * @return set of describable attributes
     */
    ImmutableSet<Attribute<?>> getDescribableAttributes();

    String describeAttributeSet(Map<Attribute<?>, ?> attributes);

    @Nullable
    String describeMissingAttribute(Attribute<?> attribute, Object consumerValue);

    String describeExtraAttribute(Attribute<?> attribute, Object producerValue);
}
