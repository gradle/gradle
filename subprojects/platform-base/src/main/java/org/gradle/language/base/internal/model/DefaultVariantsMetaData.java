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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.Named;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.Variant;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultVariantsMetaData implements VariantsMetaData {
    private final Map<String, Object> variants;

    public DefaultVariantsMetaData(Map<String, Object> variants) {
        this.variants = variants;
    }

    public static VariantsMetaData extractFrom(BinarySpec spec) {
        Map<String, Object> variants = Maps.newHashMap();
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
                    }
                }
            }
        }

        return new DefaultVariantsMetaData(ImmutableMap.copyOf(variants));
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
        if (result != null) {
            // note: it's not the role of this class to validate that the annotation is properly used, that
            // is to say only on a getter returning String or a Named instance, so we trust the result of
            // the call
            variants.put(name, result);
        }
    }


    @Override
    public Set<String> getDimensions() {
        return variants.keySet();
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
}
