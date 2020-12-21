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
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact;
import org.gradle.api.internal.artifacts.transform.AbstractTransformedArtifactSet;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.Transformation;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
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
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class LocalFileDependencyBackedArtifactSet implements ResolvedArtifactSet, LocalDependencyFiles, VariantSelector.Factory {
    private static final DisplayName LOCAL_FILE = Describables.of("local file");

    private final LocalFileDependencyMetadata dependencyMetadata;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final VariantSelector selector;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public LocalFileDependencyBackedArtifactSet(LocalFileDependencyMetadata dependencyMetadata, Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.dependencyMetadata = dependencyMetadata;
        this.componentFilter = componentFilter;
        this.selector = selector;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    public LocalFileDependencyMetadata getDependencyMetadata() {
        return dependencyMetadata;
    }

    public ArtifactTypeRegistry getArtifactTypeRegistry() {
        return artifactTypeRegistry;
    }

    public Spec<? super ComponentIdentifier> getComponentFilter() {
        return componentFilter;
    }

    public VariantSelector getSelector() {
        return selector;
    }

    @Override
    public void visit(Visitor listener) {
        FileCollectionStructureVisitor.VisitType visitType = listener.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            listener.visitArtifacts(new EndCollection(this));
            return;
        }

        ComponentIdentifier componentIdentifier = dependencyMetadata.getComponentId();
        if (componentIdentifier != null && !componentFilter.isSatisfiedBy(componentIdentifier)) {
            listener.visitArtifacts(new EndCollection(this));
            return;
        }

        FileCollectionInternal fileCollection = dependencyMetadata.getFiles();
        Set<File> files;
        try {
            files = fileCollection.getFiles();
        } catch (Exception throwable) {
            listener.visitArtifacts(new BrokenArtifacts(throwable));
            return;
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

            ImmutableAttributes variantAttributes = artifactTypeRegistry.mapAttributesFor(file);
            SingletonFileResolvedVariant variant = new SingletonFileResolvedVariant(file, artifactIdentifier, LOCAL_FILE, variantAttributes, dependencyMetadata, calculatedValueContainerFactory);
            selectedArtifacts.add(selector.select(variant, this));
        }
        CompositeResolvedArtifactSet.of(selectedArtifacts.build()).visit(listener);
        if (visitType == FileCollectionStructureVisitor.VisitType.Spec) {
            listener.visitArtifacts(new CollectionSpec(fileCollection));
        }
    }

    @Override
    public ResolvedArtifactSet asTransformed(ResolvedVariant sourceVariant, ImmutableAttributes targetAttributes, Transformation transformation, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolver, TransformedVariantFactory transformedVariantFactory) {
        return new TransformedLocalFileArtifactSet((SingletonFileResolvedVariant) sourceVariant, targetAttributes, transformation, dependenciesResolver, calculatedValueContainerFactory);
    }

    @Override
    public void visitTransformSources(TransformSourceVisitor visitor) {
        // Should not be called
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        // Should not be called
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(dependencyMetadata.getFiles().getBuildDependencies());
    }

    private static class SingletonFileResolvedVariant implements ResolvedVariant, ResolvedArtifactSet, Artifacts, ResolvedVariantSet {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final DisplayName variantName;
        private final ImmutableAttributes variantAttributes;
        private final LocalFileDependencyMetadata dependencyMetadata;
        private final ResolvableArtifact artifact;

        SingletonFileResolvedVariant(File file, ComponentArtifactIdentifier artifactIdentifier, DisplayName variantName, ImmutableAttributes variantAttributes, LocalFileDependencyMetadata dependencyMetadata, CalculatedValueContainerFactory calculatedValueContainerFactory) {
            this.artifactIdentifier = artifactIdentifier;
            this.variantName = variantName;
            this.variantAttributes = variantAttributes;
            this.dependencyMetadata = dependencyMetadata;
            artifact = new DefaultResolvableArtifact(null, DefaultIvyArtifactName.forFile(file, null), this.artifactIdentifier, this.dependencyMetadata.getFiles(), calculatedValueContainerFactory.create(Describables.of(artifactIdentifier), file), calculatedValueContainerFactory);
        }

        @Override
        public VariantResolveMetadata.Identifier getIdentifier() {
            return null;
        }

        @Override
        public String toString() {
            return asDescribable().getDisplayName();
        }

        public File getFile() {
            return artifact.getFile();
        }

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
        public void visit(Visitor visitor) {
            visitor.visitArtifacts(this);
        }

        @Override
        public void visitTransformSources(TransformSourceVisitor visitor) {
            // Should not be called
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
            // Should not be called
            throw new UnsupportedOperationException();
        }

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
        }

        @Override
        public void finalizeNow(boolean requireFiles) {
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

    /**
     * An artifact set that contains a single transformed local file.
     */
    private static class TransformedLocalFileArtifactSet extends AbstractTransformedArtifactSet implements FileCollectionInternal.Source {
        private final SingletonFileResolvedVariant delegate;

        public TransformedLocalFileArtifactSet(SingletonFileResolvedVariant delegate,
                                               ImmutableAttributes attributes,
                                               Transformation transformation,
                                               ExtraExecutionGraphDependenciesResolverFactory dependenciesResolver,
                                               CalculatedValueContainerFactory calculatedValueContainerFactory) {
            super(delegate.getComponentId(), delegate, attributes, transformation, dependenciesResolver, calculatedValueContainerFactory);
            this.delegate = delegate;
        }

        public ComponentIdentifier getOwnerId() {
            return delegate.getComponentId();
        }

        public File getFile() {
            return delegate.getFile();
        }
    }

    private static class CollectionSpec implements Artifacts {
        private final FileCollectionInternal fileCollection;

        public CollectionSpec(FileCollectionInternal fileCollection) {
            this.fileCollection = fileCollection;
        }

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
        }

        @Override
        public void finalizeNow(boolean requireFiles) {
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            visitor.visitSpec(fileCollection);
        }
    }
}
