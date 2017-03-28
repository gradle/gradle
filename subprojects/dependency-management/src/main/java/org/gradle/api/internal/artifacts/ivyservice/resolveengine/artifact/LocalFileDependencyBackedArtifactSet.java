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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LocalFileDependencyBackedArtifactSet implements DynamicResolvedArtifactSet {
    private final LocalFileDependencyMetadata dependencyMetadata;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final VariantSelector selector;
    private final ImmutableAttributesFactory attributesFactory;

    public LocalFileDependencyBackedArtifactSet(LocalFileDependencyMetadata dependencyMetadata, Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector, ImmutableAttributesFactory attributesFactory) {
        this.dependencyMetadata = dependencyMetadata;
        this.componentFilter = componentFilter;
        this.selector = selector;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public ResolvedArtifactSet snapshot() {
        ComponentIdentifier componentIdentifier = dependencyMetadata.getComponentId();
        if (componentIdentifier != null && !componentFilter.isSatisfiedBy(componentIdentifier)) {
            return ResolvedArtifactSet.EMPTY;
        }

        Set<File> files;
        try {
            files = dependencyMetadata.getFiles().getFiles();
        } catch (Throwable throwable) {
            return new FailingResolvedArtifactSetSnapshot(throwable);
        }

        List<ResolvedVariant> variants = Lists.newArrayListWithCapacity(files.size());
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

            AttributeContainerInternal variantAttributes = DefaultArtifactAttributes.forFile(file, attributesFactory);
            ResolvedVariant variant = new SingletonFileResolvedVariant(file, artifactIdentifier, variantAttributes);
            variants.add(selector.select(Collections.singleton(variant), EmptySchema.INSTANCE));
        }

        return new ResolvedVariantBackedArtifactSetSnapshot(variants, dependencyMetadata);
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        dest.add(dependencyMetadata.getFiles().getBuildDependencies());
    }

    @Override
    public void addPrepareActions(final BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
        if (visitor.includeFiles()) {
            snapshot().addPrepareActions(actions, visitor);
        }
    }

    @Override
    public void visit(final ArtifactVisitor visitor) {
        if (visitor.includeFiles()) {
            snapshot().visit(visitor);
        }
    }

    private static class SingletonFileResolvedVariant implements ResolvedVariant {
        private final File file;
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final AttributeContainerInternal variantAttributes;

        SingletonFileResolvedVariant(File file, ComponentArtifactIdentifier artifactIdentifier, AttributeContainerInternal variantAttributes) {
            this.file = file;
            this.artifactIdentifier = artifactIdentifier;
            this.variantAttributes = variantAttributes;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (visitor.includeFiles()) {
                visitor.visitFile(artifactIdentifier, variantAttributes, file);
            }
        }

        @Override
        public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, ArtifactVisitor visitor) {
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }
    }

    private class FailingResolvedArtifactSetSnapshot implements ResolvedArtifactSet {
        private final Throwable throwable;

        public FailingResolvedArtifactSetSnapshot(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, ArtifactVisitor visitor) {
            if (visitor.includeFiles()) {
                visitor.visitFailure(throwable);
            }
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (visitor.includeFiles()) {
                visitor.visitFailure(throwable);
            }
        }
    }

    private static class ResolvedVariantBackedArtifactSetSnapshot implements ResolvedArtifactSet {
        private final LocalFileDependencyMetadata dependencyMetadata;
        private final List<ResolvedVariant> variants;

        public ResolvedVariantBackedArtifactSetSnapshot(List<ResolvedVariant> variants, LocalFileDependencyMetadata dependencyMetadata) {
            this.variants = variants;
            this.dependencyMetadata = dependencyMetadata;
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            dest.add(dependencyMetadata.getFiles().getBuildDependencies());
        }

        @Override
        public void addPrepareActions(final BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
            if (!visitor.includeFiles()) {
                return;
            }

            for (ResolvedVariant variant : variants) {
                variant.addPrepareActions(actions, visitor);
            }
        }

        @Override
        public void visit(final ArtifactVisitor visitor) {
            if (!visitor.includeFiles()) {
                return;
            }

            for (ResolvedVariant variant : variants) {
                variant.visit(visitor);
            }
        }
    }
}
