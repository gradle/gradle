/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;

public class DefaultResolvedConfigurationBuilder implements ResolvedConfigurationBuilder {
    private final Long2ObjectMap<Dependency> modulesMap = new Long2ObjectOpenHashMap<>();
    private final TransientConfigurationResultsBuilder builder;

    public DefaultResolvedConfigurationBuilder(TransientConfigurationResultsBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void addFirstLevelDependency(Dependency moduleDependency, DependencyGraphNode dependency) {
        builder.firstLevelDependency(dependency.getNodeId());
        //we don't serialise the module dependencies at this stage so we need to keep track
        //of the mapping module dependency <-> resolved dependency
        modulesMap.put(dependency.getNodeId(), moduleDependency);
    }

    @Override
    public void done(DependencyGraphNode root) {
        builder.done(root.getNodeId());
    }

    @Override
    public void addChild(DependencyGraphNode parent, DependencyGraphNode child, int artifactsId) {
        builder.parentChildMapping(parent.getNodeId(), child.getNodeId(), artifactsId);
    }

    @Override
    public void addNodeArtifacts(DependencyGraphNode node, int artifactsId) {
        builder.nodeArtifacts(node.getNodeId(), artifactsId);
    }

    @Override
    public void newResolvedDependency(DependencyGraphNode node) {
        builder.resolvedDependency(node.getNodeId(), node.getComponent().getId(), node.getMetadata().getName());
    }

    @Override
    public ResolvedGraphResults complete() {
        return new DefaultResolvedGraphResults(modulesMap);
    }
}
