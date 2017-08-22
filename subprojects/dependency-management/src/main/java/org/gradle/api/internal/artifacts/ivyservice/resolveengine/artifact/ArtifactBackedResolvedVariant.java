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

import org.gradle.api.Buildable;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DownloadArtifactBuildOperationType;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.AsyncArtifactListener;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.EMPTY;

class ArtifactBackedResolvedVariant implements ResolvedVariant {
    private final DisplayName displayName;
    private final AttributeContainerInternal attributes;
    private final ResolvedArtifactSet artifacts;

    private ArtifactBackedResolvedVariant(DisplayName displayName, AttributeContainerInternal attributes, ResolvedArtifactSet artifacts) {
        this.displayName = displayName;
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    public static ResolvedVariant create(DisplayName displayName, AttributeContainerInternal attributes, Collection<? extends ResolvableArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return new ArtifactBackedResolvedVariant(displayName, attributes, EMPTY);
        }
        if (artifacts.size() == 1) {
            return new ArtifactBackedResolvedVariant(displayName, attributes, new SingleArtifactSet(attributes, artifacts.iterator().next()));
        }
        List<SingleArtifactSet> artifactSets = new ArrayList<SingleArtifactSet>();
        for (ResolvableArtifact artifact : artifacts) {
            artifactSets.add(new SingleArtifactSet(attributes, artifact));
        }
        return new ArtifactBackedResolvedVariant(displayName, attributes, CompositeResolvedArtifactSet.of(artifactSets));
    }

    @Override
    public String toString() {
        return displayName.getDisplayName();
    }

    @Override
    public DisplayName asDescribable() {
        return displayName;
    }

    @Override
    public ResolvedArtifactSet getArtifacts() {
        return artifacts;
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return attributes;
    }

    private static boolean isFromIncludedBuild(ResolvableArtifact artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        return id instanceof ProjectComponentIdentifier
            && !((ProjectComponentIdentifier) id).getBuild().isCurrentBuild();
    }

    private static class SingleArtifactSet implements ResolvedArtifactSet, ResolvedArtifactSet.Completion {
        private final AttributeContainer variantAttributes;
        private final ResolvableArtifact artifact;
        private volatile Throwable failure;

        SingleArtifactSet(AttributeContainer variantAttributes, ResolvableArtifact artifact) {
            this.variantAttributes = variantAttributes;
            this.artifact = artifact;
        }

        @Override
        public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            if (listener.requireArtifactFiles()) {
                if (isFromIncludedBuild(artifact)) {
                    // Cannot currently build these artifacts asynchronously due to various locking problems
                    // Build it now instead
                    new DownloadArtifactFile(artifact, this, listener).run(null);
                } else if (artifact.isResolved()) {
                    // Already resolved. Push the artifact to the listener now, rather than queuing it up
                    new DownloadArtifactFile(artifact, this, listener).run(null);
                } else {
                    // Resolve it later
                    actions.add(new DownloadArtifactFile(artifact, this, listener));
                }
            }
            return this;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (failure != null) {
                visitor.visitFailure(failure);
            } else {
                visitor.visitArtifact(variantAttributes, artifact);
            }
        }

        @Override
        public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
            visitor.visitDependency(((Buildable) artifact).getBuildDependencies());
        }
    }

    private static class DownloadArtifactFile implements RunnableBuildOperation {
        private final ResolvableArtifact artifact;
        private final SingleArtifactSet owner;
        private final AsyncArtifactListener listener;

        DownloadArtifactFile(ResolvableArtifact artifact, SingleArtifactSet owner, AsyncArtifactListener visitor) {
            this.artifact = artifact;
            this.owner = owner;
            this.listener = visitor;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                artifact.getFile();
                listener.artifactAvailable(artifact);

                // This method is sometimes called directly (i.e. not via an operation executor).
                // In these cases, the context is null.
                if (context != null) {
                    context.setResult(DownloadArtifactBuildOperationType.RESULT);
                }
            } catch (Throwable t) {
                owner.failure = t;
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Resolve " + artifact)
                .details(new DownloadArtifactBuildOperationType.DetailsImpl(artifact.getId().getDisplayName()));
        }
    }

}
