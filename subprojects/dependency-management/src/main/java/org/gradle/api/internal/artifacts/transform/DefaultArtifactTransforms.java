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
import com.google.common.collect.Ordering;
import com.google.common.io.Files;
import org.gradle.api.Buildable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.Attribute;
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
import org.gradle.internal.text.TreeFormatter;

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

            List<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>> candidates = new ArrayList<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>>();
            for (ResolvedVariant variant : variants) {
                AttributeContainerInternal variantAttributes = variant.getAttributes().asImmutable();
                ConsumerVariantMatchResult matchResult = new ConsumerVariantMatchResult();
                matchingCache.collectConsumerVariants(variantAttributes, requested, matchResult);
                for (ConsumerVariantMatchResult.ConsumerVariant consumerVariant : matchResult.getMatches()) {
                    candidates.add(Pair.of(variant, consumerVariant));
                }
            }
            if (candidates.size() == 1) {
                Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant> result = candidates.get(0);
                return new TransformingArtifactSet(result.getLeft().getArtifacts(), result.getRight().attributes, result.getRight().transformer);
            }

            if (!candidates.isEmpty()) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Found multiple transforms that can produce a variant for consumer attributes");
                formatter.startChildren();
                formatAttributes(formatter, requested);
                formatter.endChildren();
                formatter.node("Found the following transforms");
                formatter.startChildren();
                for (Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant> candidate : candidates) {
                    formatter.node("Transform from variant");
                    formatter.startChildren();
                    formatAttributes(formatter, candidate.getLeft().getAttributes());
                    formatter.endChildren();
                }
                formatter.endChildren();

                throw new AmbiguousTransformException(formatter.toString());
            }

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

    private void formatAttributes(TreeFormatter formatter, AttributeContainerInternal attributes) {
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
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
            List<File> transformedFiles;
            try {
                transformedFiles = transform.transform(artifact.getFile());
            } catch (Throwable t) {
                visitor.visitFailure(t);
                return;
            }

            TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();
            for (File output : transformedFiles) {
                ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(artifact.getId().getComponentIdentifier(), output.getName());
                String extension = Files.getFileExtension(output.getName());
                IvyArtifactName artifactName = new DefaultIvyArtifactName(output.getName(), extension, extension);
                ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(artifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                visitor.visitArtifact(target, resolvedArtifact);
            }
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
        public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
            List<File> result;
            try {
                result = transform.transform(file);
            } catch (Throwable t) {
                visitor.visitFailure(t);
                return;
            }
            if (!result.isEmpty()) {
                for (File outputFile : result) {
                    visitor.visitFile(new ComponentFileArtifactIdentifier(artifactIdentifier.getComponentIdentifier(), outputFile.getName()), target, outputFile);
                }
            }
        }
    }

}
