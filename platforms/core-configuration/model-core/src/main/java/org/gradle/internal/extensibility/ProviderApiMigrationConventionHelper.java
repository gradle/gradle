/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.extensibility;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detects properties whose eager getter has been replaced by a lazy property with a different
 * name (e.g. {@code outputDir} -> {@code outputDirectory}), so {@link ConventionAwareHelper}
 * can route legacy {@code getConventionMapping().map("oldName", ...)} calls to the lazy
 * property's {@code .convention()} API.
 *
 * <p>Renames are discovered from {@link ReplacedBy} on the old getter and only routed when the
 * replacement is a lazy ({@link SupportsConvention}) property. They are computed once per class
 * and cached, so lookups are constant-time without reflection.</p>
 */
class ProviderApiMigrationConventionHelper {

    private static final ClassValue<Map<String, String>> REPLACED_PROPERTIES = new ClassValue<Map<String, String>>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
            Map<String, String> replacements = new HashMap<>();
            collectReplacedProperties(type, type, replacements, new HashSet<>());
            return ImmutableMap.copyOf(replacements);
        }
    };

    /**
     * If {@code propertyName} on {@code sourceClass} (or any of its supertypes/interfaces) has
     * been replaced by a lazy property, returns the replacement property name. Otherwise returns
     * {@code null}.
     */
    @Nullable
    static String findRenamedProperty(Class<?> sourceClass, String propertyName) {
        return REPLACED_PROPERTIES.get(sourceClass).get(propertyName);
    }

    private static void collectReplacedProperties(Class<?> sourceClass, @Nullable Class<?> clazz, Map<String, String> replacements, Set<Class<?>> visited) {
        if (clazz == null || clazz == Object.class || !visited.add(clazz)) {
            return;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            ReplacedBy replacedBy = method.getDeclaredAnnotation(ReplacedBy.class);
            if (replacedBy == null) {
                continue;
            }
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            if (accessorType != PropertyAccessorType.GET_GETTER && accessorType != PropertyAccessorType.IS_GETTER) {
                continue;
            }
            String replacement = replacedBy.value();
            if (isLazyProperty(sourceClass, replacement)) {
                // Subclasses are visited first, so the most specific annotation wins
                replacements.putIfAbsent(accessorType.propertyNameFor(method), replacement);
            }
        }
        collectReplacedProperties(sourceClass, clazz.getSuperclass(), replacements, visited);
        for (Class<?> iface : clazz.getInterfaces()) {
            collectReplacedProperties(sourceClass, iface, replacements, visited);
        }
    }

    private static boolean isLazyProperty(Class<?> sourceClass, String propertyName) {
        Method getter = JavaPropertyReflectionUtil.findGetterMethod(sourceClass, propertyName);
        return getter != null && SupportsConvention.class.isAssignableFrom(getter.getReturnType());
    }

    private ProviderApiMigrationConventionHelper() {
    }
}
