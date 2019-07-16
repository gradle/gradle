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
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ResolveConfigurationResolutionBuildOperationResult implements ResolveConfigurationDependenciesBuildOperationType.Result, CustomOperationTraceSerialization {
    private final ResolutionResult resolutionResult;
    private final AttributeContainer requestedAttributes;

    ResolveConfigurationResolutionBuildOperationResult(ResolutionResult resolutionResult, AttributeContainer requestedAttributes) {
        this.resolutionResult = resolutionResult;
        this.requestedAttributes = requestedAttributes;
    }

    @Override
    public ResolvedComponentResult getRootComponent() {
        return resolutionResult.getRoot();
    }

    @Override
    public String getRepositoryId(ResolvedComponentResult resolvedComponentResult) {
        return ((ResolvedComponentResultInternal) resolvedComponentResult).getRepositoryName();
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("resolvedDependenciesCount", getRootComponent().getDependencies().size());
        final Map<String, Map<String, String>> components = Maps.newHashMap();
        resolutionResult.allComponents(new Action<ResolvedComponentResult>() {
            @Override
            public void execute(ResolvedComponentResult component) {
                components.put(
                    component.getId().getDisplayName(),
                    Collections.singletonMap("repoName", ((ResolvedComponentResultInternal) component).getRepositoryName())
                );
            }
        });
        model.put("components", components);
        ImmutableList.Builder<Object> requestedAttributesBuilder = new ImmutableList.Builder<Object>();
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

}
