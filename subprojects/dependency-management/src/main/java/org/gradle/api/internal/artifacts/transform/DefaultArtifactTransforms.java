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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.gradle.api.Buildable;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.attributes.DefaultArtifactAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.ArtifactResolveException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultArtifactTransforms implements ArtifactTransforms {

    private ArtifactAttributeMatchingCache matchingCache;

    public DefaultArtifactTransforms(ArtifactAttributeMatchingCache matchingCache) {
        this.matchingCache = matchingCache;
    }

    /**
     * Returns a selector that selects the variant matching the supplied attributes, or which can be transformed to match. The selector may return null to mean 'none of these'
     */
    public <T extends HasAttributes> Transformer<T, Collection<? extends T>> variantSelector(final AttributeContainerInternal attributes) {
        return new AttributeMatchingVariantSelector<T>(attributes.asImmutable());
    }

    /**
     * Returns a visitor that transforms files and artifacts to match the requested attributes
     * and then forwards the results to the given visitor.
     */
    public ArtifactVisitor visitor(final ArtifactVisitor visitor, @Nullable AttributeContainerInternal attributes, ImmutableAttributesFactory attributesFactory) {
        if (attributes == null || attributes.isEmpty()) {
            return visitor;
        }
        return new ArtifactTransformingVisitor(visitor, attributes.asImmutable(), attributesFactory);
    }

    private class AttributeMatchingVariantSelector<T extends HasAttributes> implements Transformer<T, Collection<? extends T>> {
        private final AttributeContainer attributes;

        private AttributeMatchingVariantSelector(AttributeContainer attributes) {
            this.attributes = attributes;
        }

        @Override
        public String toString() {
            return "Variant selector for " + attributes;
        }

        @Override
        public T transform(Collection<? extends T> variants) {
            // Note: This algorithm is a placeholder only. Should deal with ambiguous matches
            if (attributes.isEmpty()) {
                return variants.iterator().next();
            }

            // Note: This algorithm is a placeholder only. Should deal with ambiguous matches
            T canTransform = null;
            for (T variant : variants) {
                AttributeContainer variantAttributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
                if (matchingCache.areMatchingAttributes(variantAttributes, this.attributes)) {
                    return variant;
                }
                if (matchingCache.getTransform(variantAttributes, this.attributes) != null) {
                    canTransform = variant;
                }
            }
            return canTransform;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AttributeMatchingVariantSelector)) {
                return false;
            }
            AttributeMatchingVariantSelector<?> that = (AttributeMatchingVariantSelector<?>) o;
            return Objects.equal(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(attributes);
        }
    }

    private class ArtifactTransformingVisitor implements ArtifactVisitor {
        private final ArtifactVisitor visitor;
        private final AttributeContainerInternal attributes;
        private final ImmutableAttributesFactory attributesFactory;

        private ArtifactTransformingVisitor(final ArtifactVisitor visitor, @Nullable final AttributeContainerInternal attributes, ImmutableAttributesFactory attributesFactory) {
            this.visitor = visitor;
            this.attributes = attributes;
            this.attributesFactory = attributesFactory;
        }

        @Override
        public void visitArtifact(final ResolvedArtifact artifact) {
            List<ResolvedArtifact> transformResults = matchingCache.getTransformedArtifacts(artifact, attributes);
            if (transformResults == null) {
                if (matchingCache.areMatchingAttributes(artifact.getAttributes(), this.attributes)) {
                    transformResults = Collections.singletonList(artifact);
                    matchingCache.putTransformedArtifact(artifact, attributes, transformResults);
                }
            }
            if (transformResults != null) {
                for (ResolvedArtifact resolvedArtifact : transformResults) {
                    visitor.visitArtifact(resolvedArtifact);
                }
                return;
            }

            AttributeContainer artifactAttributes = ((AttributeContainerInternal) artifact.getAttributes()).asImmutable();
            final Transformer<List<File>, File> transform = matchingCache.getTransform(artifactAttributes, this.attributes);
            if (transform == null) {
                throw new ArtifactResolveException("Artifact " + artifact + " is not compatible with requested attributes " + this.attributes);
            }

            TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

            transformResults = Lists.newArrayList();
            List<File> transformedFiles = transform.transform(artifact.getFile());
            for (final File output : transformedFiles) {
                ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifact.getId().getComponentIdentifier(), output.getName());
                IvyArtifactName artifactName = DefaultIvyArtifactName.forAttributeContainer(output.getName(), this.attributes);
                ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(artifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output, this.attributes, attributesFactory);
                transformResults.add(resolvedArtifact);
                visitor.visitArtifact(resolvedArtifact);
            }
            matchingCache.putTransformedArtifact(artifact, this.attributes, transformResults);
        }

        @Override
        public boolean includeFiles() {
            return visitor.includeFiles();
        }

        @Override
        public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files) {
            List<File> result = new ArrayList<File>();
            RuntimeException transformException = null;
            try {
                for (File file : files) {
                    try {
                        List<File> transformResults = matchingCache.getTransformedFile(file, attributes);
                        if (transformResults == null) {
                            AttributeContainer fileWithAttributes = DefaultArtifactAttributes.forFile(file, attributesFactory);
                            if (matchingCache.areMatchingAttributes(fileWithAttributes, this.attributes)) {
                                transformResults = Collections.singletonList(file);
                                matchingCache.putTransformedFile(file, attributes, transformResults);
                            }
                        }
                        if (transformResults != null) {
                            result.addAll(transformResults);
                            continue;
                        }

                        AttributeContainer fileWithAttributes = DefaultArtifactAttributes.forFile(file, attributesFactory);
                        Transformer<List<File>, File> transform = matchingCache.getTransform(fileWithAttributes, attributes);
                        if (transform == null) {
                            continue;
                        }
                        transformResults = transform.transform(file);
                        matchingCache.putTransformedFile(file, attributes, transformResults);
                        result.addAll(transformResults);
                    } catch (RuntimeException e) {
                        transformException = e;
                        break;
                    }
                }
            } catch (Throwable t) {
                //TODO JJ: this lets the wrapped visitor report issues during file access
                visitor.visitFiles(componentIdentifier, files);
            }
            if (transformException != null) {
                throw transformException;
            }
            if (!result.isEmpty()) {
                visitor.visitFiles(componentIdentifier, result);
            }
        }
    }

}
