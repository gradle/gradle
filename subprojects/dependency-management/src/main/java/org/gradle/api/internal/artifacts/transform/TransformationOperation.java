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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.internal.Try;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.util.Map;

class TransformationOperation implements RunnableBuildOperation {
    private final CacheableInvocation<TransformationSubject> invocation;
    private final ComponentArtifactIdentifier artifactIdentifier;
    private final Map<ComponentArtifactIdentifier, TransformationResult> results;
    private final String displayName;
    @Nullable
    private final String progressDisplayName;

    TransformationOperation(CacheableInvocation<TransformationSubject> invocation, String displayName, @Nullable String progressDisplayName, ComponentArtifactIdentifier artifactIdentifier, Map<ComponentArtifactIdentifier, TransformationResult> results) {
        this.displayName = displayName;
        this.invocation = invocation;
        this.progressDisplayName = progressDisplayName;
        this.artifactIdentifier = artifactIdentifier;
        this.results = results;
    }

    @Override
    public void run(@Nullable BuildOperationContext context) {
        Try<TransformationSubject> transformedSubject = invocation.invoke();
        results.put(artifactIdentifier, new PrecomputedTransformationResult(transformedSubject));
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        return BuildOperationDescriptor.displayName(displayName)
            .progressDisplayName(progressDisplayName)
            .operationType(BuildOperationCategory.UNCATEGORIZED);
    }
}
