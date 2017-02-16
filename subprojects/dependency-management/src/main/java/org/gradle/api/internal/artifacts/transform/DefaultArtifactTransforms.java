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
import com.google.common.io.Files;
import org.gradle.api.Buildable;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultArtifactTransforms implements ArtifactTransforms {
    private final VariantAttributeMatchingCache matchingCache;

    public DefaultArtifactTransforms(VariantAttributeMatchingCache matchingCache) {
        this.matchingCache = matchingCache;
    }

    public Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> variantSelector(AttributeContainerInternal requested) {
        return new AttributeMatchingVariantSelector(requested.asImmutable());
    }

    private class AttributeMatchingVariantSelector implements Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> {
        private final AttributeContainerInternal requested;

        private AttributeMatchingVariantSelector(AttributeContainerInternal requested) {
            this.requested = requested;
        }

        @Override
        public String toString() {
            return "Variant selector for " + requested;
        }

        @Override
        public ResolvedArtifactSet transform(Collection<? extends ResolvedVariant> variants) {
            List<? extends ResolvedVariant> matches = matchingCache.selectMatches(variants, requested);
            if (matches.size() > 0) {
                return matches.get(0).getArtifacts();
            }
            // TODO - fail on ambiguous match

            List<Pair<ResolvedVariant, VariantAttributeMatchingCache.GeneratedVariant>> candidates = new ArrayList<Pair<ResolvedVariant, VariantAttributeMatchingCache.GeneratedVariant>>();
            for (ResolvedVariant variant : variants) {
                AttributeContainerInternal variantAttributes = variant.getAttributes().asImmutable();
                VariantAttributeMatchingCache.GeneratedVariant candidateTransform = matchingCache.getGeneratedVariant(variantAttributes, requested);
                if (candidateTransform != null) {
                    candidates.add(Pair.of(variant, candidateTransform));
                }
            }
            if (candidates.size() > 0) {
                Pair<ResolvedVariant, VariantAttributeMatchingCache.GeneratedVariant> result = candidates.get(0);
                return new TransformingArtifactSet(result.getLeft().getArtifacts(), result.getRight().attributes, result.getRight().transformer);
            }
            // TODO - fail on ambiguous match

            return ResolvedArtifactSet.EMPTY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AttributeMatchingVariantSelector)) {
                return false;
            }
            AttributeMatchingVariantSelector that = (AttributeMatchingVariantSelector) o;
            return Objects.equal(requested, that.requested);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(requested);
        }
    }

    private class TransformingArtifactSet implements ResolvedArtifactSet {
        private final ResolvedArtifactSet delegate;
        private final AttributeContainerInternal target;
        private final Transformer<List<File>, File> transform;

        TransformingArtifactSet(ResolvedArtifactSet delegate, AttributeContainerInternal target, Transformer<List<File>, File> transform) {
            this.delegate = delegate;
            this.target = target;
            this.transform = transform;
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            delegate.collectBuildDependencies(dest);
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            delegate.visit(new ArtifactTransformingVisitor(visitor, target, transform));
        }
    }

    private class ArtifactTransformingVisitor implements ArtifactVisitor {
        private final ArtifactVisitor visitor;
        private final AttributeContainerInternal target;
        private final Transformer<List<File>, File> transform;

        private ArtifactTransformingVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Transformer<List<File>, File> transform) {
            this.visitor = visitor;
            this.target = target;
            this.transform = transform;
        }

        @Override
        public void visitArtifact(AttributeContainer variant, ResolvedArtifact artifact) {
            List<ResolvedArtifact> transformResults = matchingCache.getTransformedArtifacts(artifact, target);
            if (transformResults != null) {
                for (ResolvedArtifact resolvedArtifact : transformResults) {
                    visitor.visitArtifact(target, resolvedArtifact);
                }
                return;
            }

            List<File> transformedFiles;
            try {
                transformedFiles = transform.transform(artifact.getFile());
            } catch (Throwable t) {
                visitor.visitFailure(t);
                return;
            }

            TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();
            transformResults = Lists.newArrayListWithCapacity(transformedFiles.size());
            for (File output : transformedFiles) {
                ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifact.getId().getComponentIdentifier(), output.getName());
                String extension = Files.getFileExtension(output.getName());
                IvyArtifactName artifactName = new DefaultIvyArtifactName(output.getName(), extension, extension);
                ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(artifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                transformResults.add(resolvedArtifact);
                visitor.visitArtifact(target, resolvedArtifact);
            }

            matchingCache.putTransformedArtifact(artifact, this.target, transformResults);
        }

        @Override
        public void visitFailure(Throwable failure) {
            visitor.visitFailure(failure);
        }

        @Override
        public boolean includeFiles() {
            return visitor.includeFiles();
        }

        @Override
        public void visitFiles(@Nullable ComponentIdentifier componentIdentifier, AttributeContainer variant, Iterable<File> files) {
            List<File> result = new ArrayList<File>();
            try {
                for (File file : files) {
                    try {
                        List<File> transformResults = matchingCache.getTransformedFile(file, target);
                        if (transformResults != null) {
                            result.addAll(transformResults);
                            continue;
                        }

                        transformResults = transform.transform(file);
                        matchingCache.putTransformedFile(file, target, transformResults);
                        result.addAll(transformResults);
                    } catch (Throwable t) {
                        visitor.visitFailure(t);
                    }
                }
            } catch (Throwable t) {
                visitor.visitFailure(t);
                return;
            }
            if (!result.isEmpty()) {
                visitor.visitFiles(componentIdentifier, target, result);
            }
        }
    }

}
