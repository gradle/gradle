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
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Actions;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

import static org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult.eachElement;

class ResolveConfigurationResolutionBuildOperationResult implements ResolveConfigurationDependenciesBuildOperationType.Result, CustomOperationTraceSerialization {
    private final Supplier<ResolvedComponentResultInternal> rootSource;
    private final Lazy<ImmutableAttributes> desugaredAttributes;

    public ResolveConfigurationResolutionBuildOperationResult(
        Supplier<ResolvedComponentResultInternal> rootSource,
        ImmutableAttributes requestedAttributes,
        AttributeDesugaring attributeDesugaring
    ) {
        this.rootSource = rootSource;
        this.desugaredAttributes = Lazy.unsafe().of(() -> attributeDesugaring.desugar(requestedAttributes));
    }

    @Override
    public ResolvedComponentResult getRootComponent() {
        return rootSource.get();
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
        eachElement(rootSource.get(), component -> components.put(
            component.getId().getDisplayName(),
            Collections.singletonMap("repoId", getRepositoryId(component))
        ), Actions.doNothing(), new HashSet<>());
        model.put("components", components);

        AttributeContainer requestedAttributes = desugaredAttributes.get();
        ImmutableList.Builder<Object> requestedAttributesBuilder = new ImmutableList.Builder<>();
        for (Attribute<?> att : requestedAttributes.keySet()) {
            requestedAttributesBuilder.add(ImmutableMap.of("name", att.getName(), "value", requestedAttributes.getAttribute(att).toString()));
        }
        model.put("requestedAttributes", requestedAttributesBuilder.build());

        return model;
    }

    @Override
    public AttributeContainer getRequestedAttributes() {
        return desugaredAttributes.get();
    }

}
