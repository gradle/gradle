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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collection;
import java.util.Set;

class ArtifactBackedResolvedVariant implements ResolvedVariant {
    private final AttributeContainerInternal attributes;
    private final Set<ResolvedArtifact> artifacts;

    private ArtifactBackedResolvedVariant(AttributeContainerInternal attributes, Collection<? extends ResolvedArtifact> artifacts) {
        this.attributes = attributes;
        this.artifacts = ImmutableSet.copyOf(artifacts);
    }

    public static ResolvedVariant create(AttributeContainerInternal attributes, Collection<? extends ResolvedArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return new EmptyResolvedVariant(attributes);
        }
        if (artifacts.size() == 1) {
            return new SingleArtifactResolvedVariant(attributes, artifacts.iterator().next());
        }
        return new ArtifactBackedResolvedVariant(attributes, artifacts);
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        for (ResolvedArtifact artifact : artifacts) {
            visitor.visitArtifact(attributes, artifact);
        }
    }

    @Override
    public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, ArtifactVisitor visitor) {
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        for (ResolvedArtifact artifact : artifacts) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return attributes;
    }

    private static class SingleArtifactResolvedVariant implements ResolvedVariant {
        private final AttributeContainerInternal variantAttributes;
        private final ResolvedArtifact artifact;

        SingleArtifactResolvedVariant(AttributeContainerInternal variantAttributes, ResolvedArtifact artifact) {
            this.variantAttributes = variantAttributes;
            this.artifact = artifact;
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }

        @Override
        public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, ArtifactVisitor visitor) {
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            visitor.visitArtifact(variantAttributes, artifact);
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }

}
