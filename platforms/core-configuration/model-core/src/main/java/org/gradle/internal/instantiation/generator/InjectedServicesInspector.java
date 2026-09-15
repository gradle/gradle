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

package org.gradle.internal.instantiation.generator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.instantiation.generator.ClassGenerator.GeneratedClass;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.GeneratedSubclasses.unpack;

/**
 * Collects the injected services of a generated class and its nested managed classes.
 */
@NullMarked
class InjectedServicesInspector {
    private final ConstructorSelector constructorSelector;

    public InjectedServicesInspector(ConstructorSelector constructorSelector) {
        this.constructorSelector = constructorSelector;
    }

    /**
     * Returns the constructor and property injections of the type and its nested managed types,
     * grouped by declaring type.
     */
    public <T> Map<Class<?>, List<Class<?>>> declaredInjectedServices(Class<T> type, GeneratedClass<? extends T> generatedClass) {
        Map<Class<?>, List<Class<?>>> injectedServicesByDeclaringType = new LinkedHashMap<>();
        Set<Class<?>> visited = new HashSet<>();
        visited.add(type);
        collectInjectedServices(type, generatedClass, visited, injectedServicesByDeclaringType);
        return ImmutableMap.copyOf(injectedServicesByDeclaringType);
    }

    private void collectInjectedServices(Class<?> type, GeneratedClass<?> generatedClass, Set<Class<?>> visited, Map<Class<?>, List<Class<?>>> injectedServicesByDeclaringType) {
        List<Class<?>> injectedServices = ImmutableList.<Class<?>>builder()
            .add(constructorSelector.forType(type).getParameterTypes())
            .addAll(generatedClass.getInjectedServices())
            .build();
        if (!injectedServices.isEmpty()) {
            injectedServicesByDeclaringType.put(type, injectedServices);
        }
        for (GeneratedClass<?> nestedManagedClass : generatedClass.getNestedManagedClasses()) {
            Class<?> nestedType = unpack(nestedManagedClass.getGeneratedClass());
            if (visited.add(nestedType)) {
                collectInjectedServices(nestedType, nestedManagedClass, visited, injectedServicesByDeclaringType);
            }
        }
    }
}
