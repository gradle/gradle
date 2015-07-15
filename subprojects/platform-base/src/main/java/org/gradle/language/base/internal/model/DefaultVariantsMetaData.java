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
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.Variant;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

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

    public static VariantsMetaData extractFrom(BinarySpec spec) {
        Map<String, Object> variants = Maps.newHashMap();
        Map<String, ModelType<?>> dimensionTypes = Maps.newHashMap();
        Class<? extends BinarySpec> specClass = spec.getClass();
        Set<Class<?>> interfaces = ClassInspector.inspect(specClass).getSuperTypes();
        for (Class<?> intf : interfaces) {
            ClassDetails details = ClassInspector.inspect(intf);
            Collection<? extends PropertyDetails> properties = details.getProperties();
            for (PropertyDetails property : properties) {
                List<Method> getters = property.getGetters();
                for (Method getter : getters) {
                    if (getter.getAnnotation(Variant.class) != null) {
                        extractVariant(variants, spec, property.getName(), getter);
                        dimensionTypes.put(property.getName(), ModelType.of(getter.getReturnType()));
                    }
                }
            }
        }

        return new DefaultVariantsMetaData(Collections.unmodifiableMap(variants), ImmutableMap.copyOf(dimensionTypes));
    }

    private static void extractVariant(Map<String, Object> variants, BinarySpec spec, String name, Method method) {
        Object result;
        try {
            result = method.invoke(spec);
        } catch (IllegalAccessException e) {
            result = null;
        } catch (InvocationTargetException e) {
            result = null;
        }

        // note: it's not the role of this class to validate that the annotation is properly used, that
        // is to say only on a getter returning String or a Named instance, so we trust the result of
        // the call
        variants.put(name, result);

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
