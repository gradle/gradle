/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class LocalFileDependencyBackedArtifactSet implements ResolvedArtifactSet, LocalDependencyFiles {
    private static final DisplayName LOCAL_FILE = Describables.of("local file");

    private final LocalFileDependencyMetadata dependencyMetadata;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final VariantSelector selector;
    private final ArtifactTypeRegistry artifactTypeRegistry;

    public LocalFileDependencyBackedArtifactSet(LocalFileDependencyMetadata dependencyMetadata, Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector, ArtifactTypeRegistry artifactTypeRegistry) {
        this.dependencyMetadata = dependencyMetadata;
        this.componentFilter = componentFilter;
        this.selector = selector;
        this.artifactTypeRegistry = artifactTypeRegistry;
    }

    @Override
    public void visitSpec(FileCollectionStructureVisitor visitor) {
        dependencyMetadata.getFiles().visitStructure(visitor);
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        FileCollectionStructureVisitor.VisitType visitType = listener.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            return EMPTY_RESULT;
        }

        ComponentIdentifier componentIdentifier = dependencyMetadata.getComponentId();
        if (componentIdentifier != null && !componentFilter.isSatisfiedBy(componentIdentifier)) {
            return EMPTY_RESULT;
        }

        FileCollectionInternal fileCollection = dependencyMetadata.getFiles();
        Set<File> files;
        try {
            files = fileCollection.getFiles();
        } catch (Exception throwable) {
            return new BrokenResolvedArtifactSet(throwable);
        }

        ImmutableList.Builder<ResolvedArtifactSet> selectedArtifacts = ImmutableList.builderWithExpectedSize(files.size());
        for (File file : files) {
            ComponentArtifactIdentifier artifactIdentifier;
            if (componentIdentifier == null) {
                artifactIdentifier = new OpaqueComponentArtifactIdentifier(file);
                if (!componentFilter.isSatisfiedBy(artifactIdentifier.getComponentIdentifier())) {
                    continue;
                }
            } else {
                artifactIdentifier = new ComponentFileArtifactIdentifier(componentIdentifier, file.getName());
            }

            AttributeContainerInternal variantAttributes = artifactTypeRegistry.mapAttributesFor(file);
            SingletonFileResolvedVariant variant = new SingletonFileResolvedVariant(file, artifactIdentifier, LOCAL_FILE, variantAttributes, dependencyMetadata);
            selectedArtifacts.add(selector.select(variant));
        }
        Completion result = CompositeResolvedArtifactSet.of(selectedArtifacts.build()).startVisit(actions, listener);
        if (visitType == FileCollectionStructureVisitor.VisitType.Spec) {
            return visitor -> {
                result.visit(visitor);
                visitor.visitSpec(fileCollection);
            };
        }
        return result;
    }

    @Override
    public void visitLocalArtifacts(LocalArtifactVisitor listener) {
        // Artifacts are not known until the file collection is queried
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(dependencyMetadata.getFiles().getBuildDependencies());
    }

    private static class SingletonFileResolvedVariant implements ResolvedVariant, ResolvedArtifactSet, Completion, ResolvedVariantSet {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final DisplayName variantName;
        private final AttributeContainerInternal variantAttributes;
        private final LocalFileDependencyMetadata dependencyMetadata;
        private final ResolvableArtifact artifact;

        SingletonFileResolvedVariant(File file, ComponentArtifactIdentifier artifactIdentifier, DisplayName variantName, AttributeContainerInternal variantAttributes, LocalFileDependencyMetadata dependencyMetadata) {
            this.artifactIdentifier = artifactIdentifier;
            this.variantName = variantName;
            this.variantAttributes = variantAttributes;
            this.dependencyMetadata = dependencyMetadata;
            artifact = new PreResolvedResolvableArtifact(null, DefaultIvyArtifactName.forFile(file, null), this.artifactIdentifier, file, this.dependencyMetadata.getFiles());
        }

        @Override
        public String toString() {
            return asDescribable().getDisplayName();
        }

        @Override
        public ComponentIdentifier getComponentId() {
            return artifactIdentifier.getComponentIdentifier();
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return this;
        }

        @Override
        public DisplayName asDescribable() {
            return Describables.of(artifactIdentifier);
        }

        @Override
        public Set<ResolvedVariant> getVariants() {
            return Collections.singleton(this);
        }

        @Override
        public ImmutableAttributes getOverriddenAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public AttributesSchemaInternal getSchema() {
            return EmptySchema.INSTANCE;
        }

        @Override
        public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            listener.artifactAvailable(artifact);
            return this;
        }

        @Override
        public void visitLocalArtifacts(LocalArtifactVisitor listener) {
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            visitor.visitArtifact(variantName, variantAttributes, artifact);
            visitor.endVisitCollection(FileCollectionInternal.OTHER);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(dependencyMetadata.getFiles().getBuildDependencies());
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }
    }
}
