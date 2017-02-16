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

import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class LocalFileDependencyBackedArtifactSet implements ResolvedArtifactSet {
    private final LocalFileDependencyMetadata dependencyMetadata;
    private final Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector;
    private final ImmutableAttributesFactory attributesFactory;

    public LocalFileDependencyBackedArtifactSet(LocalFileDependencyMetadata dependencyMetadata, Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector, ImmutableAttributesFactory attributesFactory) {
        this.dependencyMetadata = dependencyMetadata;
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

        Set<File> files;
        try {
            files = dependencyMetadata.getFiles().getFiles();
        } catch (Throwable throwable) {
            visitor.visitFailure(throwable);
            return;
        }

        for (final File file : files) {
            AttributeContainerInternal variantAttributes = DefaultArtifactAttributes.forFile(file, attributesFactory);
            ResolvedVariant variant = new DefaultResolvedVariant(file, dependencyMetadata.getComponentId(), variantAttributes);
            selector.transform(Collections.singleton(variant)).visit(visitor);
        }
    }

    private static class SingletonFileResolvedArtifactSet implements ResolvedArtifactSet {
        private final ComponentIdentifier componentIdentifier;
        private final File file;
        private final AttributeContainer variantAttributes;


        SingletonFileResolvedArtifactSet(File file, @Nullable ComponentIdentifier componentIdentifier, AttributeContainer variantAttributes) {
            this.file = file;
            this.componentIdentifier = componentIdentifier;
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
                visitor.visitFiles(componentIdentifier, variantAttributes, Collections.singletonList(file));
            }
        }
    }

    private static class DefaultResolvedVariant implements ResolvedVariant {
        private final File file;
        private final ComponentIdentifier componentIdentifier;
        private final AttributeContainerInternal variantAttributes;

        DefaultResolvedVariant(File file, ComponentIdentifier componentIdentifier, AttributeContainerInternal variantAttributes) {
            this.file = file;
            this.componentIdentifier = componentIdentifier;
            this.variantAttributes = variantAttributes;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return new SingletonFileResolvedArtifactSet(file, componentIdentifier, variantAttributes);
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }
    }
}
