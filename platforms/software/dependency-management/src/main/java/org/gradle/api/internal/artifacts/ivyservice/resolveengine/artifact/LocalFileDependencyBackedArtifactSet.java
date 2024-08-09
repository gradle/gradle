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
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformChain;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformedArtifactSet;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Abstract file dependency implementation. The two {@code default} and {@code deserialized} subtypes
 * represent the artifact set before and after configuration cache serialization. The deserialized
 * type only stores a subset of the information originally stored by the default type.
 *
 * <p>This is required since the files in a given file dependency artifact set are unknown until
 * dependencies are executed. For this reason, we delay artifact selection until after this artifact
 * set is restored from the configuration cache. This differs from normal artifact variant selection
 * where we can perform selection before serialization.</p>
 *
 * <p>The tricky part that due to the artifactType registry, artifact variant selection depends on the
 * file names of the artifacts exposed by a variant. Normal variants have access to these file names
 * before the dependencies are executed, but file dependencies do not.</p>
 *
 * <p>We should do one of these things to fix the current mess here:</p>
 * <ul>
 *     <li>Kill file dependencies</li>
 *     <li>Enhance file dependencies to know what files they produce</li>
 *     <li>Kill artifactType registry</li>
 * </ul>
 */
public abstract class LocalFileDependencyBackedArtifactSet implements TransformedArtifactSet, LocalDependencyFiles, ArtifactVariantSelector.ResolvedArtifactTransformer {
    private static final DisplayName LOCAL_FILE = Describables.of("local file");

    private final LocalFileDependencyMetadata dependencyMetadata;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final ArtifactVariantSelector variantSelector;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final boolean allowNoMatchingVariants;

    public LocalFileDependencyBackedArtifactSet(
        LocalFileDependencyMetadata dependencyMetadata,
        Spec<? super ComponentIdentifier> componentFilter,
        ArtifactVariantSelector variantSelector,
        ArtifactTypeRegistry artifactTypeRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        boolean allowNoMatchingVariants
    ) {
        this.dependencyMetadata = dependencyMetadata;
        this.componentFilter = componentFilter;
        this.variantSelector = variantSelector;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.allowNoMatchingVariants = allowNoMatchingVariants;
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

    public ArtifactVariantSelector getVariantSelector() {
        return variantSelector;
    }

    public boolean getAllowNoMatchingVariants() {
        return allowNoMatchingVariants;
    }

    public abstract ImmutableAttributes getRequestAttributes();

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
            selectedArtifacts.add(variantSelector.select(variant, getRequestAttributes(), allowNoMatchingVariants, this));
        }
        CompositeResolvedArtifactSet.of(selectedArtifacts.build()).visit(listener);
    }

    @Override
    public ResolvedArtifactSet asTransformed(ResolvedVariant sourceVariant, VariantDefinition variantDefinition, TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory, TransformedVariantFactory transformedVariantFactory) {
        return new TransformedLocalFileArtifactSet((SingletonFileResolvedVariant) sourceVariant, variantDefinition.getTargetAttributes(), variantDefinition.getTransformChain(), dependenciesResolverFactory, calculatedValueContainerFactory);
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

        @Override
        @Nonnull
        public ComponentIdentifier getComponentIdentifier() {
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
        public List<ResolvedVariant> getVariants() {
            return Collections.singletonList(this);
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
        public void visit(ArtifactVisitor visitor) {
            visitor.visitArtifact(variantName, variantAttributes, ImmutableCapabilities.EMPTY, artifact);
            visitor.endVisitCollection(FileCollectionInternal.OTHER);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(dependencyMetadata.getFiles().getBuildDependencies());
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return variantAttributes;
        }

        @Override
        public ImmutableCapabilities getCapabilities() {
            return ImmutableCapabilities.EMPTY;
        }
    }

    /**
     * An artifact set that contains a single transformed local file.
     */
    private static class TransformedLocalFileArtifactSet extends AbstractTransformedArtifactSet implements FileCollectionInternal.Source {
        private final SingletonFileResolvedVariant delegate;

        public TransformedLocalFileArtifactSet(SingletonFileResolvedVariant delegate,
                                               ImmutableAttributes attributes,
                                               TransformChain transformChain,
                                               TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory,
                                               CalculatedValueContainerFactory calculatedValueContainerFactory) {
            super(delegate.getComponentIdentifier(), delegate, attributes, ImmutableCapabilities.EMPTY, transformChain, dependenciesResolverFactory, calculatedValueContainerFactory);
            this.delegate = delegate;
        }
    }
}
