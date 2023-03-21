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

package org.gradle.execution.plan;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.taskgraph.NodeIdentity;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * A Gradle user home level registry of {@link ToPlannedNodeConverter} instances.
 * <p>
 * All the available converters are expected to support disjoint set of {@link Node node types}.
 */
@NonNullApi
@ThreadSafe
@ServiceScope(Scopes.UserHome.class)
public class ToPlannedNodeConverterRegistry {

    private static final ToPlannedNodeConverter MISSING_MARKER = new MissingToPlannedNodeConverter();

    private final List<ToPlannedNodeConverter> converters;

    private final ConcurrentMap<Class<? extends Node>, ToPlannedNodeConverter> convertersByNodeType = new ConcurrentHashMap<>();

    public ToPlannedNodeConverterRegistry(List<ToPlannedNodeConverter> converters) {
        validateConverters(converters);
        this.converters = ImmutableList.copyOf(converters);

        for (ToPlannedNodeConverter converter : this.converters) {
            convertersByNodeType.put(converter.getSupportedNodeType(), converter);
        }
    }

    /**
     * Returns a set of node types that this converter registry can provide.
     */
    public Set<NodeIdentity.NodeType> getConvertedNodeTypes() {
        return converters.stream()
            .map(ToPlannedNodeConverter::getConvertedNodeType)
            .collect(Collectors.toSet());
    }

    /**
     * Returns a converter for the given node, or null if there is no converter for the node.
     */
    @Nullable
    public ToPlannedNodeConverter getConverter(Node node) {
        Class<? extends Node> nodeType = node.getClass();
        ToPlannedNodeConverter converter = convertersByNodeType.computeIfAbsent(nodeType, this::findConverter);
        return converter == MISSING_MARKER ? null : converter;
    }

    private ToPlannedNodeConverter findConverter(Class<? extends Node> nodeType) {
        for (ToPlannedNodeConverter converterCandidate : converters) {
            Class<? extends Node> supportedNodeType = converterCandidate.getSupportedNodeType();
            if (supportedNodeType.isAssignableFrom(nodeType)) {
                return converterCandidate;
            }
        }

        return MISSING_MARKER;
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

    private static final class MissingToPlannedNodeConverter implements ToPlannedNodeConverter {
        @Override
        public Class<? extends Node> getSupportedNodeType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NodeIdentity.NodeType getConvertedNodeType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NodeIdentity getNodeIdentity(Node node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInSamePlan(Node node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlannedNodeInternal convert(Node node, List<? extends NodeIdentity> nodeDependencies) {
            throw new UnsupportedOperationException();
        }
    }

}
