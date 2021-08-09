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
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.DownloadArtifactBuildOperationType;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet.EMPTY;

public class ArtifactBackedResolvedVariant implements ResolvedVariant {
    private final VariantResolveMetadata.Identifier identifier;
    private final DisplayName displayName;
    private final AttributeContainerInternal attributes;
    private final CapabilitiesMetadata capabilities;
    private final ResolvedArtifactSet artifacts;

    private ArtifactBackedResolvedVariant(@Nullable VariantResolveMetadata.Identifier identifier, DisplayName displayName, AttributeContainerInternal attributes, CapabilitiesMetadata capabilities, ResolvedArtifactSet artifacts) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.artifacts = artifacts;
    }

    public static ResolvedVariant create(@Nullable VariantResolveMetadata.Identifier identifier, DisplayName displayName, AttributeContainerInternal attributes, CapabilitiesMetadata capabilities, Collection<? extends ResolvableArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, EMPTY);
        }
        if (artifacts.size() == 1) {
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, new SingleArtifactSet(displayName, attributes, capabilities, artifacts.iterator().next()));
        }
        List<SingleArtifactSet> artifactSets = new ArrayList<>(artifacts.size());
        for (ResolvableArtifact artifact : artifacts) {
            artifactSets.add(new SingleArtifactSet(displayName, attributes, capabilities, artifact));
        }
        return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, CompositeResolvedArtifactSet.of(artifactSets));
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

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return capabilities;
    }

    private static class SingleArtifactSet implements ResolvedArtifactSet, ResolvedArtifactSet.Artifacts {
        private final DisplayName variantName;
        private final AttributeContainer variantAttributes;
        private final CapabilitiesMetadata capabilities;
        private final ResolvableArtifact artifact;

        SingleArtifactSet(DisplayName variantName, AttributeContainer variantAttributes, CapabilitiesMetadata capabilities, ResolvableArtifact artifact) {
            this.variantName = variantName;
            this.variantAttributes = variantAttributes;
            this.capabilities = capabilities;
            this.artifact = artifact;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visitArtifacts(this);
        }

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
            if (requireFiles) {
                if (artifact.isResolveSynchronously()) {
                    // Resolve it now
                    artifact.getFileSource().finalizeIfNotAlready();
                } else {
                    // Resolve it later
                    actions.add(new DownloadArtifactFile(artifact));
                }
            }
        }

        @Override
        public void finalizeNow(boolean requireFiles) {
            if (requireFiles) {
                artifact.getFileSource().finalizeIfNotAlready();
            }
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (visitor.requireArtifactFiles() && !artifact.getFileSource().getValue().isSuccessful()) {
                visitor.visitFailure(artifact.getFileSource().getValue().getFailure().get());
            } else {
                visitor.visitArtifact(variantName, variantAttributes, capabilities.getCapabilities(), artifact);
                visitor.endVisitCollection(FileCollectionInternal.OTHER);
            }
        }

        @Override
        public void visitTransformSources(TransformSourceVisitor visitor) {
            if (artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
                visitor.visitArtifact(artifact);
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

    private static class DownloadArtifactFile implements RunnableBuildOperation {
        private final ResolvableArtifact artifact;

        DownloadArtifactFile(ResolvableArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public void run(BuildOperationContext context) {
            artifact.getFileSource().finalizeIfNotAlready();
            context.setResult(DownloadArtifactBuildOperationType.RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Resolve " + artifact)
                .details(new DownloadArtifactBuildOperationType.DetailsImpl(artifact.getId().getDisplayName()));
        }
    }

}
