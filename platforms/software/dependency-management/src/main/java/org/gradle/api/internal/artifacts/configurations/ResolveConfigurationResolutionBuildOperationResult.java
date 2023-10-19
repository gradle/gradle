/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Named;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ResolveConfigurationResolutionBuildOperationResult implements ResolveConfigurationDependenciesBuildOperationType.Result, CustomOperationTraceSerialization {
    private final ResolutionResult resolutionResult;
    private final AttributeContainer requestedAttributes;

    static ResolveConfigurationResolutionBuildOperationResult create(ResolutionResult resolutionResult, ImmutableAttributesFactory attributesFactory) {
        return new ResolveConfigurationResolutionBuildOperationResult(
                resolutionResult,
                new LazyDesugaringAttributeContainer(resolutionResult.getRequestedAttributes(), attributesFactory)
        );
    }

    private ResolveConfigurationResolutionBuildOperationResult(ResolutionResult resolutionResult, AttributeContainer requestedAttributes) {
        this.resolutionResult = resolutionResult;
        this.requestedAttributes = requestedAttributes;
    }

    @Override
    public ResolvedComponentResult getRootComponent() {
        return resolutionResult.getRoot();
    }

    @Override
    public String getRepositoryId(ResolvedComponentResult resolvedComponentResult) {
        return ((ResolvedComponentResultInternal) resolvedComponentResult).getRepositoryId();
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("resolvedDependenciesCount", getRootComponent().getDependencies().size());
        final Map<String, Map<String, String>> components = Maps.newHashMap();
        resolutionResult.allComponents(component -> components.put(
            component.getId().getDisplayName(),
            Collections.singletonMap("repoId", getRepositoryId(component))
        ));
        model.put("components", components);
        ImmutableList.Builder<Object> requestedAttributesBuilder = new ImmutableList.Builder<>();
        for (Attribute<?> att : requestedAttributes.keySet()) {
            requestedAttributesBuilder.add(ImmutableMap.of("name", att.getName(), "value", requestedAttributes.getAttribute(att).toString()));
        }
        model.put("requestedAttributes", requestedAttributesBuilder.build());
        return model;
    }

    @Override
    public AttributeContainer getRequestedAttributes() {
        return requestedAttributes;
    }

    // This does almost the same thing as passing through DesugaredAttributeContainerSerializer / DesugaringAttributeContainerSerializer.
    // Those make some assumptions about allowed attribute value types that we can't - we serialize everything else to a string instead.
    private static final class LazyDesugaringAttributeContainer implements ImmutableAttributes {

        private final AttributeContainer source;
        private final ImmutableAttributesFactory attributesFactory;
        private ImmutableAttributes desugared;

        private LazyDesugaringAttributeContainer(@Nullable AttributeContainer source, ImmutableAttributesFactory attributesFactory) {
            this.source = source;
            this.attributesFactory = attributesFactory;
        }

        @Override
        public ImmutableSet<Attribute<?>> keySet() {
            return getDesugared().keySet();
        }

        @Deprecated
        @Override
        public <T> AttributeContainer attribute(Attribute<T> key, T value) {
            return getDesugared().attribute(key, value);
        }

        @Deprecated
        @Override
        public <T> AttributeContainer attributeProvider(Attribute<T> key, Provider<? extends T> provider) {
            return getDesugared().attributeProvider(key, provider);
        }

        @Nullable
        @Override
        public <T> T getAttribute(Attribute<T> key) {
            return getDesugared().getAttribute(key);
        }

        @Override
        public boolean isEmpty() {
            return getDesugared().isEmpty();
        }

        @Override
        public boolean contains(Attribute<?> key) {
            return getDesugared().contains(key);
        }

        @Override
        public AttributeContainer getAttributes() {
            return getDesugared().getAttributes();
        }

        @Override
        public ImmutableAttributes asImmutable() {
            return getDesugared();
        }

        @Override
        public Map<Attribute<?>, ?> asMap() {
            return getDesugared().asMap();
        }

        @Override
        public <T> AttributeValue<T> findEntry(Attribute<T> key) {
            return getDesugared().findEntry(key);
        }

        @Override
        public AttributeValue<?> findEntry(String key) {
            return getDesugared().findEntry(key);
        }

        @Override
        public String toString() {
            return getDesugared().toString();
        }

        @Override
        public boolean equals(Object obj) {
            return getDesugared().equals(obj);
        }

        @Override
        public int hashCode() {
            return getDesugared().hashCode();
        }

        private ImmutableAttributes getDesugared() {
            if (desugared == null) {
                desugarAttributes();
            }
            return desugared;
        }

        @SuppressWarnings("unchecked")
        private void desugarAttributes() {
            AttributeContainerInternal result = attributesFactory.mutable();
            if (source != null) {
                for (Attribute<?> attribute : source.keySet()) {
                    String name = attribute.getName();
                    Class<?> type = attribute.getType();
                    Object attributeValue = source.getAttribute(attribute);
                    if (type.equals(Boolean.class)) {
                        result.attribute((Attribute<Boolean>) attribute, (Boolean) attributeValue);
                    } else if (type.equals(String.class)) {
                        result.attribute((Attribute<String>) attribute, (String) attributeValue);
                    } else if (type.equals(Integer.class)) {
                        result.attribute((Attribute<Integer>) attribute, (Integer) attributeValue);
                    } else {
                        // just serialize as a String as best we can
                        Attribute<String> stringAtt = Attribute.of(name, String.class);
                        String stringValue;
                        if (attributeValue instanceof Named) {
                            stringValue = ((Named) attributeValue).getName();
                        } else if (attributeValue instanceof Object[]) { // don't bother trying to handle primitive arrays specially
                            stringValue = Arrays.toString((Object[]) attributeValue);
                        } else {
                            stringValue = attributeValue.toString();
                        }
                        result.attribute(stringAtt, stringValue);
                    }
                }
            }
            desugared = result.asImmutable();
        }
    }

}
