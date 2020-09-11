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

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DownloadArtifactBuildOperationType;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.AsyncArtifactListener;
import org.gradle.api.internal.artifacts.transform.TransformationSubject;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.EMPTY;

public class ArtifactBackedResolvedVariant implements ResolvedVariant {
    private final VariantResolveMetadata.Identifier identifier;
    private final DisplayName displayName;
    private final AttributeContainerInternal attributes;
    private final ResolvedArtifactSet artifacts;

    private ArtifactBackedResolvedVariant(@Nullable VariantResolveMetadata.Identifier identifier, DisplayName displayName, AttributeContainerInternal attributes, ResolvedArtifactSet artifacts) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    public static ResolvedVariant create(@Nullable VariantResolveMetadata.Identifier identifier, DisplayName displayName, AttributeContainerInternal attributes, Collection<? extends ResolvableArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, EMPTY);
        }
        if (artifacts.size() == 1) {
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, new SingleArtifactSet(displayName, attributes, artifacts.iterator().next()));
        }
        List<SingleArtifactSet> artifactSets = new ArrayList<>(artifacts.size());
        for (ResolvableArtifact artifact : artifacts) {
            artifactSets.add(new SingleArtifactSet(displayName, attributes, artifact));
        }
        return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, CompositeResolvedArtifactSet.of(artifactSets));
    }

    @Override
    public VariantResolveMetadata.Identifier getIdentifier() {
        return identifier;
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

    private static class SingleArtifactSet implements ResolvedArtifactSet, ResolvedArtifactSet.Completion {
        private final DisplayName variantName;
        private final AttributeContainer variantAttributes;
        private final ResolvableArtifact artifact;
        private volatile Throwable failure;

        SingleArtifactSet(DisplayName variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
            this.variantName = variantName;
            this.variantAttributes = variantAttributes;
            this.artifact = artifact;
        }

        @Override
        public ResolvedArtifactSet.Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            if (listener.requireArtifactFiles()) {
                if (artifact.isResolveSynchronously()) {
                    // Resolve it now
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
                visitor.visitArtifact(variantName, variantAttributes, artifact);
                visitor.endVisitCollection(FileCollectionInternal.OTHER);
            }
        }

        @Override
        public void visitLocalArtifacts(LocalArtifactVisitor visitor) {
            if (artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
                visitor.visitArtifact(new SingleLocalArtifactSet(artifact));
            }
        }

        @Override
        public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
            if (!(artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier)) {
                visitor.execute(artifact);
            }
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(artifact);
        }

        @Override
        public String toString() {
            return artifact.getId().getDisplayName();
        }
    }

    public static class SingleLocalArtifactSet implements ResolvedArtifactSet.LocalArtifactSet {
        private final ResolvableArtifact artifact;

        public SingleLocalArtifactSet(ResolvableArtifact artifact) {
            this.artifact = artifact;
        }

        public ResolvableArtifact getArtifact() {
            return artifact;
        }

        @Override
        public Object getId() {
            return artifact.getId();
        }

        @Override
        public String getDisplayName() {
            return artifact.getId().getDisplayName();
        }

        @Override
        public TaskDependencyContainer getTaskDependencies() {
            return artifact;
        }

        @Override
        public TransformationSubject calculateSubject() {
            return TransformationSubject.initial(artifact.getId(), artifact.getFile());
        }

        @Override
        public ResolvableArtifact transformedTo(File output) {
            return artifact.transformedTo(output);
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
            } catch (Exception t) {
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
