/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;

import java.util.List;
import java.util.Set;

class RootNode extends NodeState implements RootGraphNode {
    private final ResolveOptimizations resolveOptimizations;
    private final List<? extends DependencyMetadata> syntheticDependencies;

    RootNode(long resultId, ComponentState moduleRevision, ResolveState resolveState, List<? extends DependencyMetadata> syntheticDependencies, VariantGraphResolveState root) {
        super(resultId, moduleRevision, resolveState, root, false);
        this.resolveOptimizations = resolveState.getResolveOptimizations();
        this.syntheticDependencies = syntheticDependencies;
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    @Override
    public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
        return getResolveState().getFiles();
    }

    @Override
    void addIncomingEdge(EdgeState dependencyEdge) {
        throw new InvalidUserCodeException(
            "Cannot select root node '" + getMetadata().getName() + "' as a variant. " +
                "Configurations should not act as both a resolution root and a variant simultaneously. " +
                "Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them."
        );
    }

    @Override
    public boolean isSelected() {
        return true;
    }

    @Override
    public void deselect() {
    }

    @Override
    public LocalVariantGraphResolveState getResolveState() {
        return (LocalVariantGraphResolveState) super.getResolveState();
    }

    @Override
    public LocalVariantGraphResolveMetadata getMetadata() {
        return (LocalVariantGraphResolveMetadata) super.getMetadata();
    }

    @Override
    public ResolveOptimizations getResolveOptimizations() {
        return resolveOptimizations;
    }

    @Override
    protected List<? extends DependencyMetadata> getAllDependencies() {
        List<? extends DependencyMetadata> superDependencies = super.getAllDependencies();
        if (syntheticDependencies.isEmpty()) {
            return superDependencies;
        }
        int expectedSize = superDependencies.size() + syntheticDependencies.size();
        ImmutableList.Builder<DependencyMetadata> allDependencies = ImmutableList.builderWithExpectedSize(expectedSize);
        allDependencies.addAll(superDependencies);
        allDependencies.addAll(syntheticDependencies);
        return allDependencies.build();
    }
}
