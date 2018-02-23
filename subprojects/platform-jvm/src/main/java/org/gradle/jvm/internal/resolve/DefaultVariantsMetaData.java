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
package org.gradle.jvm.internal.resolve;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Named;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.VariantAspect;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultVariantsMetaData implements VariantsMetaData {
    private final Map<String, Object> variantCoordinates;
    private final Set<String> allVariantAxes;
    private final Set<String> nonNullVariantAxes;
    private final Map<String, ModelType<?>> variantAxisTypes;

    private DefaultVariantsMetaData(Map<String, Object> variantCoordinates, Map<String, ModelType<?>> variantAxisTypes) {
        this.variantCoordinates = variantCoordinates;
        this.allVariantAxes = variantCoordinates.keySet();
        this.nonNullVariantAxes = ImmutableSet.copyOf(Maps.filterEntries(variantCoordinates, new Predicate<Map.Entry<String, Object>>() {
            @Override
            public boolean apply(Map.Entry<String, Object> input) {
                return input.getValue()!=null;
            }
        }).keySet());
        this.variantAxisTypes = variantAxisTypes;
    }

    public static VariantsMetaData extractFrom(BinarySpec binarySpec, ModelSchema<?> binarySpecSchema) {
        Map<String, Object> variants = Maps.newLinkedHashMap();
        ImmutableMap.Builder<String, ModelType<?>> dimensionTypesBuilder = ImmutableMap.builder();
        if (binarySpecSchema instanceof StructSchema) {
            VariantAspect variantAspect = ((StructSchema<?>) binarySpecSchema).getAspect(VariantAspect.class);
            if (variantAspect != null) {
                for (ModelProperty<?> property : variantAspect.getDimensions()) {
                    // note: it's not the role of this class to validate that the annotation is properly used, that
                    // is to say only on a getter returning String or a Named instance, so we trust the result of
                    // the call
                    Object value = property.getPropertyValue(binarySpec);
                    variants.put(property.getName(), value);
                    dimensionTypesBuilder.put(property.getName(), property.getType());
                }
            }
        }
        return new DefaultVariantsMetaData(Collections.unmodifiableMap(variants), dimensionTypesBuilder.build());
    }

    @Override
    public Set<String> getDeclaredVariantAxes() {
        return allVariantAxes;
    }

    @Override
    public Set<String> getNonNullVariantAxes() {
        return nonNullVariantAxes;
    }

    @Override
    public String getValueAsString(String variantAxis) {
        Object o = variantCoordinates.get(variantAxis);
        if (o instanceof Named) {
            return ((Named) o).getName();
        }
        if (o instanceof String) {
            return (String) o;
        }
        return o == null ? null : o.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Named> T getValueAsType(Class<T> clazz, String variantAxis) {
        return (T) variantCoordinates.get(variantAxis);
    }

    @Override
    public ModelType<?> getVariantAxisType(String variantAxis) {
        return variantAxisTypes.get(variantAxis);
    }
}
