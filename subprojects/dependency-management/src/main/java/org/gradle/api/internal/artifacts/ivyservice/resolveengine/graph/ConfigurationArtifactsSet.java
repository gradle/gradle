/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleResolutionFilter;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.resolve.resolver.ArtifactResolver;

import java.util.Set;

// TODO:DAZ Probably want to resolve early for external modules, and only hang onto Configuration node for local components
class ConfigurationArtifactsSet extends AbstractResolvedArtifactSet {
    private final DependencyGraphBuilder.ConfigurationNode childConfiguration;
    private final ModuleResolutionFilter selector;

    public ConfigurationArtifactsSet(DependencyGraphBuilder.ConfigurationNode childConfiguration, ModuleResolutionFilter selector, ArtifactResolver artifactResolver, long id) {
        super(id, childConfiguration.toId(), childConfiguration.metaData.getComponent(), artifactResolver);
        this.childConfiguration = childConfiguration;
        this.selector = selector;
    }

    @Override
    protected Set<ComponentArtifactMetaData> resolveComponentArtifacts() {
        return childConfiguration.getArtifacts(selector);
    }
}
