/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.model;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Named;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.VariantAspect;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultVariantsMetaData implements VariantsMetaData {
    private final Map<String, Object> variants;
    private final Set<String> allVariantDimensions;
    private final Set<String> nonNullVariantDimensions;
    private final Map<String, ModelType<?>> variantDimensionTypes;

    private DefaultVariantsMetaData(Map<String, Object> variants, Map<String, ModelType<?>> variantDimensionTypes) {
        this.variants = variants;
        this.allVariantDimensions = variants.keySet();
        this.nonNullVariantDimensions = ImmutableSet.copyOf(Maps.filterEntries(variants, new Predicate<Map.Entry<String, Object>>() {
            @Override
            public boolean apply(Map.Entry<String, Object> input) {
                return input.getValue()!=null;
            }
        }).keySet());
        this.variantDimensionTypes = variantDimensionTypes;
    }

    public static VariantsMetaData extractFrom(BinarySpec spec, ModelSchemaStore schemaStore) {
        Map<String, Object> variants = Maps.newLinkedHashMap();
        ImmutableMap.Builder<String, ModelType<?>> dimensionTypesBuilder = ImmutableMap.builder();
        ModelSchema<?> schema = schemaStore.getSchema(((BinarySpecInternal)spec).getPublicType());
        if (schema instanceof ModelStructSchema) {
            VariantAspect variantAspect = ((ModelStructSchema<?>) schema).getAspect(VariantAspect.class);
            if (variantAspect != null) {
                for (ModelProperty<?> property : variantAspect.getDimensions()) {
                    // note: it's not the role of this class to validate that the annotation is properly used, that
                    // is to say only on a getter returning String or a Named instance, so we trust the result of
                    // the call
                    Object value = property.getPropertyValue(spec);
                    variants.put(property.getName(), value);
                    dimensionTypesBuilder.put(property.getName(), property.getType());
                }
            }
        }
        return new DefaultVariantsMetaData(Collections.unmodifiableMap(variants), dimensionTypesBuilder.build());
    }

    @Override
    public Set<String> getAllDimensions() {
        return allVariantDimensions;
    }

    @Override
    public Set<String> getNonNullDimensions() {
        return nonNullVariantDimensions;
    }

    @Override
    public String getValueAsString(String dimension) {
        Object o = variants.get(dimension);
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
    public <T extends Named> T getValueAsType(Class<T> clazz, String dimension) {
        return (T) variants.get(dimension);
    }

    @Override
    public ModelType<?> getDimensionType(String dimension) {
        return variantDimensionTypes.get(dimension);
    }
}
