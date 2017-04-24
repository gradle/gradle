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

public class LocalFileDependencyBackedArtifactSet implements ResolvedArtifactSet {
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
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        if (!listener.includeFileDependencies()) {
            return EMPTY_RESULT;
        }

        ComponentIdentifier componentIdentifier = dependencyMetadata.getComponentId();
        if (componentIdentifier != null && !componentFilter.isSatisfiedBy(componentIdentifier)) {
            return EMPTY_RESULT;
        }

        Set<File> files;
        try {
            files = dependencyMetadata.getFiles().getFiles();
        } catch (Throwable throwable) {
            return new BrokenResult(throwable);
        }

        List<ResolvedArtifactSet> selectedArtifacts = Lists.newArrayListWithCapacity(files.size());
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
            ResolvedVariant variant = new SingletonFileResolvedVariant(file, artifactIdentifier, variantAttributes, dependencyMetadata);
            selectedArtifacts.add(selector.select(Collections.singleton(variant), EmptySchema.INSTANCE));
        }

        return CompositeArtifactSet.of(selectedArtifacts).startVisit(actions, listener);
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        dest.add(dependencyMetadata.getFiles().getBuildDependencies());
    }

    private static class SingletonFileResolvedVariant implements ResolvedVariant, ResolvedArtifactSet, Completion {
        private final File file;
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final AttributeContainerInternal variantAttributes;
        private final LocalFileDependencyMetadata dependencyMetadata;

        SingletonFileResolvedVariant(File file, ComponentArtifactIdentifier artifactIdentifier, AttributeContainerInternal variantAttributes, LocalFileDependencyMetadata dependencyMetadata) {
            this.file = file;
            this.artifactIdentifier = artifactIdentifier;
            this.variantAttributes = variantAttributes;
            this.dependencyMetadata = dependencyMetadata;
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return this;
        }

        @Override
        public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            if (listener.includeFileDependencies()) {
                listener.fileAvailable(file);
                return this;
            }
            return EMPTY_RESULT;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            visitor.visitFile(artifactIdentifier, variantAttributes, file);
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            dest.add(dependencyMetadata.getFiles().getBuildDependencies());
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }
    }

    private class BrokenResult implements Completion {
        private final Throwable throwable;

        BrokenResult(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            visitor.visitFailure(throwable);
        }
    }
}
