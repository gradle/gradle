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

package org.gradle.internal.classpath.types;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InstrumentingTypeRegistry for checking type information for external plugins.
 * It combines data of direct super types that when visiting external plugins classes and Gradle core types data, to calculated final information for external types.
 */
public class ExternalPluginsInstrumentingTypeRegistry implements InstrumentingTypeRegistry {

    private final InstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry;
    private final Map<String, Set<String>> directSuperTypes;
    private final Map<String, Set<String>> superTypes = new ConcurrentHashMap<>();

    public ExternalPluginsInstrumentingTypeRegistry(Map<String, Set<String>> directSuperTypes, InstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry) {
        this.directSuperTypes = ImmutableMap.copyOf(directSuperTypes);
        this.gradleCoreInstrumentingTypeRegistry = gradleCoreInstrumentingTypeRegistry;
    }

    @Override
    public Set<String> getSuperTypes(String type) {
        // TODO: This can probably be optimized: optimize it if it has performance impact
        Set<String> superTypes = Collections.emptySet();
        if (type.startsWith("org/gradle")) {
            superTypes = gradleCoreInstrumentingTypeRegistry.getSuperTypes(type);
        }
        return !superTypes.isEmpty() ? superTypes : computeAndCacheSuperTypesForExternalType(type);
    }

    private Set<String> computeAndCacheSuperTypesForExternalType(String type) {
        return Collections.unmodifiableSet(this.superTypes.computeIfAbsent(type, this::computeSuperTypes));
    }

    private Set<String> computeSuperTypes(String type) {
        Queue<String> superTypes = new ArrayDeque<>(getDirectSuperTypes(type));
        Set<String> collected = new HashSet<>();
        collected.add(type);
        while (!superTypes.isEmpty()) {
            String superType = superTypes.poll();
            if (collected.add(superType)) {
                superTypes.addAll(getDirectSuperTypes(superType));
            }
        }
        return collected;
    }

    private Set<String> getDirectSuperTypes(String type) {
        Set<String> superTypes = directSuperTypes.getOrDefault(type, Collections.emptySet());
        if (!superTypes.isEmpty() || !type.startsWith("org/gradle")) {
            return superTypes;
        }
        return gradleCoreInstrumentingTypeRegistry.getSuperTypes(type);
    }

    @Override
    public boolean isEmpty() {
        return directSuperTypes.isEmpty() && gradleCoreInstrumentingTypeRegistry.isEmpty();
    }
}
