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
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ComponentResult;

import java.util.Set;

public class DefaultArtifactResolutionResult implements ArtifactResolutionResult {
    private final Set<ComponentResult> componentResults;

    public DefaultArtifactResolutionResult(Set<ComponentResult> componentResults) {
        this.componentResults = componentResults;
    }

    public Set<ComponentResult> getComponents() {
        return componentResults;
    }

    public Set<ComponentArtifactsResult> getResolvedComponents() {
        Set<ComponentArtifactsResult> resolvedComponentResults = Sets.newLinkedHashSet();
        for (ComponentResult componentResult : componentResults) {
            if (componentResult instanceof ComponentArtifactsResult) {
                resolvedComponentResults.add((ComponentArtifactsResult) componentResult);
            }
        }
        return resolvedComponentResults;
    }
}
