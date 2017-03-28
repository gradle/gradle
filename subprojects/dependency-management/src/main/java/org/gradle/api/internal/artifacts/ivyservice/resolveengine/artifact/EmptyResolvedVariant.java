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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collection;

public class EmptyResolvedVariant implements ResolvedVariant {
    private final AttributeContainerInternal variantAttributes;

    public EmptyResolvedVariant(AttributeContainerInternal variantAttributes) {
        this.variantAttributes = variantAttributes;
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return variantAttributes;
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
    }

    @Override
    public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, ArtifactVisitor visitor) {
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
    }
}
