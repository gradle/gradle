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
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.gradle.api.Buildable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.EmptyResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Pair;
import org.gradle.internal.component.AmbiguousVariantSelectionException;
import org.gradle.internal.component.NoMatchingVariantSelectionException;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultArtifactTransforms implements ArtifactTransforms {
    private final VariantAttributeMatchingCache matchingCache;
    private final AttributesSchemaInternal schema;

    public DefaultArtifactTransforms(VariantAttributeMatchingCache matchingCache, AttributesSchemaInternal schema) {
        this.matchingCache = matchingCache;
        this.schema = schema;
    }

    public VariantSelector variantSelector(AttributeContainerInternal consumerAttributes, boolean allowNoMatchingVariants) {
        return new AttributeMatchingVariantSelector(consumerAttributes.asImmutable(), allowNoMatchingVariants);
    }

    private class AttributeMatchingVariantSelector implements VariantSelector {
        private final AttributeContainerInternal requested;
        private final boolean ignoreWhenNoMatches;

        private AttributeMatchingVariantSelector(AttributeContainerInternal requested, boolean ignoreWhenNoMatches) {
            this.requested = requested;
            this.ignoreWhenNoMatches = ignoreWhenNoMatches;
        }

        @Override
        public String toString() {
            return "Variant selector for " + requested;
        }

        @Override
        public ResolvedVariant select(Collection<? extends ResolvedVariant> variants, AttributesSchemaInternal producerSchema) {
            AttributeMatcher matcher = schema.withProducer(producerSchema);
            List<? extends ResolvedVariant> matches = matcher.matches(variants, requested);
            if (matches.size() == 1) {
                return matches.get(0);
            }
            if (matches.size() > 1) {
                throw new AmbiguousVariantSelectionException(requested, matches, matcher);
            }

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
                return new ConsumerProvidedResolvedVariant(result.getLeft(), result.getRight().attributes, result.getRight().transformer);
            }

            if (!candidates.isEmpty()) {
                throw new AmbiguousTransformException(requested, candidates);
            }

            if (ignoreWhenNoMatches) {
                return new EmptyResolvedVariant(requested);
            }
            throw new NoMatchingVariantSelectionException(requested, variants, matcher);
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

    private class ConsumerProvidedResolvedVariant implements ResolvedVariant {
        private final ResolvedVariant delegate;
        private final AttributeContainerInternal attributes;
        private final Transformer<List<File>, File> transform;
        private final Map<ResolvedArtifact, Throwable> artifactFailures = Maps.newConcurrentMap();
        private final Map<File, Throwable> fileFailures = Maps.newConcurrentMap();

        ConsumerProvidedResolvedVariant(ResolvedVariant delegate, AttributeContainerInternal target, Transformer<List<File>, File> transform) {
            this.delegate = delegate;
            this.attributes = target;
            this.transform = transform;
        }

        @Override
        public void addPrepareActions(final BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
            delegate.visit(new ArtifactVisitor() {
                @Override
                public void visitArtifact(AttributeContainer variant, final ResolvedArtifact artifact) {
                    actions.add(new TransformArtifactOperation(artifact));
                }

                @Override
                public boolean shouldPerformPreemptiveDownload() {
                    return visitor.shouldPerformPreemptiveDownload();
                }

                @Override
                public boolean includeFiles() {
                    return visitor.includeFiles();
                }

                @Override
                public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, final File file) {
                    actions.add(new TransformFileOperation(file));
                }

                @Override
                public void visitFailure(Throwable failure) {
                    visitor.visitFailure(failure);
                }
            });
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            delegate.visit(new ArtifactTransformingVisitor(visitor, attributes, transform, artifactFailures, fileFailures));
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            delegate.collectBuildDependencies(dest);
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return attributes;
        }

        private class TransformArtifactOperation implements RunnableBuildOperation {
            private final ResolvedArtifact artifact;

            TransformArtifactOperation(ResolvedArtifact artifact) {
                this.artifact = artifact;
            }

            @Override
            public void run() {
                try {
                    transform.transform(artifact.getFile());
                } catch (Throwable t) {
                    artifactFailures.put(artifact, t);
                }
            }

            @Override
            public String getDescription() {
                return "Apply " + transform + " to " + artifact;
            }
        }

        private class TransformFileOperation implements RunnableBuildOperation {
            private final File file;

            TransformFileOperation(File file) {
                this.file = file;
            }

            @Override
            public void run() {
                try {
                    transform.transform(file);
                } catch (Throwable t) {
                    fileFailures.put(file, t);
                }
            }

            @Override
            public String getDescription() {
                return "Apply " + transform + " to " + file;
            }
        }
    }

    private static class ArtifactTransformingVisitor implements ArtifactVisitor {
        private final ArtifactVisitor visitor;
        private final AttributeContainerInternal target;
        private final Transformer<List<File>, File> transform;
        private final Map<ResolvedArtifact, Throwable> artifactFailures;
        private final Map<File, Throwable> fileFailures;

        private ArtifactTransformingVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Transformer<List<File>, File> transform, Map<ResolvedArtifact, Throwable> artifactFailures, Map<File, Throwable> fileFailures) {
            this.visitor = visitor;
            this.target = target;
            this.transform = transform;
            this.artifactFailures = artifactFailures;
            this.fileFailures = fileFailures;
        }

        @Override
        public void visitArtifact(AttributeContainer variant, ResolvedArtifact artifact) {
            if (artifactFailures.containsKey(artifact)) {
                visitor.visitFailure(artifactFailures.get(artifact));
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
        public boolean shouldPerformPreemptiveDownload() {
            return visitor.shouldPerformPreemptiveDownload();
        }

        @Override
        public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
            if (fileFailures.containsKey(file)) {
                visitor.visitFailure(fileFailures.get(file));
                return;
            }

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
