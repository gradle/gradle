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

package org.gradle.internal.service.scopes;

import com.google.common.collect.ImmutableList;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.ToPlannedNodeConverter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Gradle user home level registry of {@link ToPlannedNodeConverter} instances.
 * <p>
 * All the available converters are expected to support disjoined set of {@link Node node types}.
 */
public class ToPlannedNodeConverterRegistry {

    private final List<ToPlannedNodeConverter> converters;

    private final Map<Class<?>, ToPlannedNodeConverter> convertersByNodeType = new HashMap<>();
    private final Set<Class<?>> unsupportedNodeTypes = new HashSet<>();

    public ToPlannedNodeConverterRegistry(List<ToPlannedNodeConverter> converters) {
        validateConverters(converters);
        this.converters = ImmutableList.copyOf(converters);

        for (ToPlannedNodeConverter converter : this.converters) {
            convertersByNodeType.put(converter.getSupportedNodeType(), converter);
        }
    }

    /**
     * Returns a converter for the given node, or null if there is no converter for the node.
     */
    @Nullable
    public ToPlannedNodeConverter getConverter(Node node) {
        Class<? extends Node> nodeType = node.getClass();
        ToPlannedNodeConverter converter = convertersByNodeType.get(nodeType);
        if (converter != null) {
            return converter;
        }

        if (unsupportedNodeTypes.contains(nodeType)) {
            return null;
        }

        for (ToPlannedNodeConverter converterCandidate : converters) {
            Class<? extends Node> supportedNodeType = converterCandidate.getSupportedNodeType();
            if (supportedNodeType.isAssignableFrom(nodeType)) {
                converter = converterCandidate;
                break;
            }
        }

        if (converter != null) {
            convertersByNodeType.put(nodeType, converter);
        } else {
            unsupportedNodeTypes.add(nodeType);
        }

        return converter;
    }

    private static void validateConverters(List<ToPlannedNodeConverter> converters) {
        int converterCount = converters.size();
        for (int i = 0; i < converterCount; i++) {
            ToPlannedNodeConverter converter1 = converters.get(i);
            for (int j = i + 1; j < converterCount; j++) {
                ToPlannedNodeConverter converter2 = converters.get(j);
                checkOverlappingConverters(converter1, converter2);
            }
        }
    }

    private static void checkOverlappingConverters(ToPlannedNodeConverter converter1, ToPlannedNodeConverter converter2) {
        Class<? extends Node> supportedNodeType1 = converter1.getSupportedNodeType();
        Class<? extends Node> supportedNodeType2 = converter2.getSupportedNodeType();
        if (supportedNodeType1.isAssignableFrom(supportedNodeType2) || supportedNodeType2.isAssignableFrom(supportedNodeType1)) {
            throw new IllegalStateException("Converter " + converter1 + " overlaps by supported node type with converter " + converter2);
        }
    }

}
