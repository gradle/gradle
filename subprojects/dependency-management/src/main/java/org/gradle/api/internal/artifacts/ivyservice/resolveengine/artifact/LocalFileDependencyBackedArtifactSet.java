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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class LocalFileDependencyBackedArtifactSet implements ResolvedArtifactSet {
    private final LocalFileDependencyMetadata dependencyMetadata;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector;
    private final ImmutableAttributesFactory attributesFactory;

    public LocalFileDependencyBackedArtifactSet(LocalFileDependencyMetadata dependencyMetadata, Spec<? super ComponentIdentifier> componentFilter, Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector, ImmutableAttributesFactory attributesFactory) {
        this.dependencyMetadata = dependencyMetadata;
        this.componentFilter = componentFilter;
        this.selector = selector;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return Collections.emptySet();
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        dest.add(dependencyMetadata.getFiles().getBuildDependencies());
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        if (!visitor.includeFiles()) {
            return;
        }

        ComponentIdentifier componentIdentifier = dependencyMetadata.getComponentId();
        if (componentIdentifier != null && !componentFilter.isSatisfiedBy(componentIdentifier)) {
            return;
        }

        Set<File> files;
        try {
            files = dependencyMetadata.getFiles().getFiles();
        } catch (Throwable throwable) {
            visitor.visitFailure(throwable);
            return;
        }

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
            ResolvedVariant variant = new DefaultResolvedVariant(file, artifactIdentifier, variantAttributes);
            selector.transform(Collections.singleton(variant)).visit(visitor);
        }
    }

    private static class SingletonFileResolvedArtifactSet implements ResolvedArtifactSet {
        private final File file;
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final AttributeContainer variantAttributes;


        SingletonFileResolvedArtifactSet(File file, ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variantAttributes) {
            this.file = file;
            this.artifactIdentifier = artifactIdentifier;
            this.variantAttributes = variantAttributes;
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            return Collections.emptySet();
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (visitor.includeFiles()) {
                visitor.visitFile(artifactIdentifier, variantAttributes, file);
            }
        }
    }

    private static class DefaultResolvedVariant implements ResolvedVariant {
        private final File file;
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final AttributeContainerInternal variantAttributes;

        DefaultResolvedVariant(File file, ComponentArtifactIdentifier artifactIdentifier, AttributeContainerInternal variantAttributes) {
            this.file = file;
            this.artifactIdentifier = artifactIdentifier;
            this.variantAttributes = variantAttributes;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return new SingletonFileResolvedArtifactSet(file, artifactIdentifier, variantAttributes);
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }
    }
}
