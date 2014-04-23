/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.result;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.Component;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.artifacts.result.ResolvedComponentArtifactsResult;
import org.gradle.api.internal.artifacts.dsl.dependencies.ComponentTransformer;

import java.util.List;
import java.util.Set;

// TODO:DAZ Unit tests
public class DefaultArtifactResolutionResult implements ArtifactResolutionResult {
    private final Set<ComponentResult> componentResults;
    private final List<ComponentTransformer> transformers;

    public DefaultArtifactResolutionResult(Set<ComponentResult> componentResults, List<ComponentTransformer> transformers) {
        this.componentResults = componentResults;
        this.transformers = transformers;
    }

    public Set<ComponentResult> getComponents() {
        return componentResults;
    }

    public <T extends Component> Set<T> getResolvedComponents(Class<T> type) {
        for (ComponentTransformer transformer : transformers) {
            if (type.isAssignableFrom(transformer.getType())) {
                return getTypedComponents(type, transformer);
            }
        }
        throw new IllegalArgumentException("Not a known component type: " + type);
    }

    private <S extends Component, T extends S> Set<T> getTypedComponents(Class<T> type, ComponentTransformer<S> transformer) {
        Set<T> libraries = Sets.newLinkedHashSet();
        for (ComponentResult componentResult : componentResults) {
            if (componentResult instanceof ResolvedComponentArtifactsResult) {
                S typedComponent = transformer.transform((ResolvedComponentArtifactsResult) componentResult);
                libraries.add(type.cast(typedComponent));
            }
        }
        return libraries;
    }

}
