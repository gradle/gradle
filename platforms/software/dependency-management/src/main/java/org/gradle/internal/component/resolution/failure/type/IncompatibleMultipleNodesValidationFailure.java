/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.type;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.interfaces.GraphNodesValidationFailure;

import java.util.List;
import java.util.Set;

/**
 * A {@link GraphNodesValidationFailure} that represents the situation when multiple incompatible variants of a single component
 * are selected during a request.
 */
public final class IncompatibleMultipleNodesValidationFailure extends AbstractResolutionFailure implements GraphNodesValidationFailure {
    private final ComponentGraphResolveMetadata selectedComponent;
    private final Set<VariantGraphResolveMetadata> incompatibleNodes;
    private final ImmutableList<AssessedCandidate> assessedCandidates;

    public IncompatibleMultipleNodesValidationFailure(ComponentGraphResolveMetadata selectedComponent, Set<VariantGraphResolveMetadata> incompatibleNodes, List<AssessedCandidate> assessedCandidates) {
        super(ResolutionFailureProblemId.INCOMPATIBLE_MULTIPLE_NODES);
        this.selectedComponent = selectedComponent;
        this.incompatibleNodes = ImmutableSet.copyOf(incompatibleNodes);
        this.assessedCandidates = ImmutableList.copyOf(assessedCandidates);
    }

    @Override
    public String describeRequestTarget() {
        return selectedComponent.getModuleVersionId().toString();
    }

    @Override
    public ComponentGraphResolveMetadata getFailingComponent() {
        return selectedComponent;
    }

    @Override
    public Set<VariantGraphResolveMetadata> getFailingNodes() {
        return incompatibleNodes;
    }

    public ImmutableList<AssessedCandidate> getAssessedCandidates() {
        return assessedCandidates;
    }
}
