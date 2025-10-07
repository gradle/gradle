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
import org.gradle.api.Named;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Actions;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

import static org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult.eachElement;

class ResolveConfigurationResolutionBuildOperationResult implements ResolveConfigurationDependenciesBuildOperationType.Result, CustomOperationTraceSerialization {
    private final Supplier<ResolvedDependencyGraph> graphSource;
    private final Lazy<AttributeContainer> lazyDesugaredAttributes;

    public ResolveConfigurationResolutionBuildOperationResult(
        Supplier<ResolvedDependencyGraph> graphSource,
        ImmutableAttributes requestedAttributes,
        AttributesFactory attributesFactory
    ) {
        this.graphSource = graphSource;
        this.lazyDesugaredAttributes = Lazy.unsafe().of(() -> desugarAttributes(attributesFactory, requestedAttributes));
    }

    @Override
    public ResolvedComponentResult getRootComponent() {
        return graphSource.get().getRootComponent();
    }

    @Override
    public String getRepositoryId(ResolvedComponentResult resolvedComponentResult) {
        return ((ResolvedComponentResultInternal) resolvedComponentResult).getRepositoryId();
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("resolvedDependenciesCount", getRootComponent().getDependencies().size());

        final Map<String, Map<String, String>> components = new HashMap<>();
        eachElement(getRootComponent(), component -> components.put(
            component.getId().getDisplayName(),
            Collections.singletonMap("repoId", getRepositoryId(component))
        ), Actions.doNothing(), new HashSet<>());
        model.put("components", components);

        ImmutableList.Builder<Object> requestedAttributesBuilder = new ImmutableList.Builder<>();
        AttributeContainer desugared = lazyDesugaredAttributes.get();
        for (Attribute<?> att : desugared.keySet()) {
            requestedAttributesBuilder.add(ImmutableMap.of("name", att.getName(), "value", desugared.getAttribute(att).toString()));
        }
        model.put("requestedAttributes", requestedAttributesBuilder.build());

        return model;
    }

    @Override
    public AttributeContainer getRequestedAttributes() {
        return lazyDesugaredAttributes.get();
    }

    // This does almost the same thing as passing through DesugaredAttributeContainerSerializer / DesugaringAttributeContainerSerializer.
    // Those make some assumptions about allowed attribute value types that we can't - we serialize everything else to a string instead.
    @SuppressWarnings("unchecked")
    private static ImmutableAttributes desugarAttributes(
        AttributesFactory attributesFactory,
        AttributeContainer source
    ) {
        AttributeContainerInternal result = attributesFactory.mutable();
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

        return result.asImmutable();
    }

}
