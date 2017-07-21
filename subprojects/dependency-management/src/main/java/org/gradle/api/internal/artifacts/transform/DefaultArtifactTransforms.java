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

import com.google.common.io.Files;
import org.gradle.api.Buildable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.failures.ResolutionFailure;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.failures.AbstractResolutionFailure;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
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
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultArtifactTransforms implements ArtifactTransforms {
    private final VariantAttributeMatchingCache matchingCache;
    private final AttributesSchemaInternal schema;

    public DefaultArtifactTransforms(VariantAttributeMatchingCache matchingCache, AttributesSchemaInternal schema) {
        this.matchingCache = matchingCache;
        this.schema = schema;
    }

    public VariantSelector variantSelector(AttributeContainerInternal consumerAttributes, boolean allowNoMatchingVariants) {
        return new AttributeMatchingVariantSelector(matchingCache, schema, consumerAttributes.asImmutable(), allowNoMatchingVariants);
    }

    private static class AttributeMatchingVariantSelector implements VariantSelector {
        private final VariantAttributeMatchingCache matchingCache;
        private final AttributesSchemaInternal schema;
        private final AttributeContainerInternal requested;
        private final boolean ignoreWhenNoMatches;

        private AttributeMatchingVariantSelector(VariantAttributeMatchingCache matchingCache, AttributesSchemaInternal schema, AttributeContainerInternal requested, boolean ignoreWhenNoMatches) {
            this.matchingCache = matchingCache;
            this.schema = schema;
            this.requested = requested;
            this.ignoreWhenNoMatches = ignoreWhenNoMatches;
        }

        @Override
        public String toString() {
            return "Variant selector for " + requested;
        }

        @Override
        public ResolvedArtifactSet select(ResolvedVariantSet producer) {
            try {
                return doSelect(producer);
            } catch (Throwable t) {
                ComponentIdentifier id = producer.getComponentIdentifier();
                return new BrokenResolvedArtifactSet(AbstractResolutionFailure.of(id, t));
            }
        }

        private ResolvedArtifactSet doSelect(ResolvedVariantSet producer) {
            AttributeMatcher matcher = schema.withProducer(producer.getSchema());
            List<? extends ResolvedVariant> matches = matcher.matches(producer.getVariants(), requested);
            if (matches.size() == 1) {
                return matches.get(0).getArtifacts();
            }
            if (matches.size() > 1) {
                throw new AmbiguousVariantSelectionException(producer.asDescribable().getDisplayName(), requested, matches, matcher);
            }

            List<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>> candidates = new ArrayList<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>>();
            for (ResolvedVariant variant : producer.getVariants()) {
                AttributeContainerInternal variantAttributes = variant.getAttributes().asImmutable();
                ConsumerVariantMatchResult matchResult = new ConsumerVariantMatchResult();
                matchingCache.collectConsumerVariants(variantAttributes, requested, matchResult);
                for (ConsumerVariantMatchResult.ConsumerVariant consumerVariant : matchResult.getMatches()) {
                    candidates.add(Pair.of(variant, consumerVariant));
                }
            }
            if (candidates.size() == 1) {
                Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant> result = candidates.get(0);
                return new ConsumerProvidedResolvedVariant(result.getLeft().getArtifacts(), result.getRight().attributes, result.getRight().transformer);
            }

            if (!candidates.isEmpty()) {
                throw new AmbiguousTransformException(producer.asDescribable().getDisplayName(), requested, candidates);
            }

            if (ignoreWhenNoMatches) {
                return ResolvedArtifactSet.EMPTY;
            }
            throw new NoMatchingVariantSelectionException(producer.asDescribable().getDisplayName(), requested, producer.getVariants(), matcher);
        }
    }

    private static class ConsumerProvidedResolvedVariant implements ResolvedArtifactSet {
        private final ResolvedArtifactSet delegate;
        private final AttributeContainerInternal attributes;
        private final Transformer<List<File>, File> transform;

        ConsumerProvidedResolvedVariant(ResolvedArtifactSet delegate, AttributeContainerInternal target, Transformer<List<File>, File> transform) {
            this.delegate = delegate;
            this.attributes = target;
            this.transform = transform;
        }

        @Override
        public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            Map<ResolvableArtifact, TransformArtifactOperation> artifactResults = new ConcurrentHashMap<ResolvableArtifact, TransformArtifactOperation>();
            Map<File, TransformFileOperation> fileResults = new ConcurrentHashMap<File, TransformFileOperation>();
            Completion result = delegate.startVisit(actions, new TransformingAsyncArtifactListener(artifactResults, actions, transform, listener, fileResults));
            return new TransformingResult(result, artifactResults, fileResults);
        }

        @Override
        public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
            delegate.collectBuildDependencies(visitor);
        }

        private static class TransformingAsyncArtifactListener implements AsyncArtifactListener {
            private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
            private final BuildOperationQueue<RunnableBuildOperation> actions;
            private final AsyncArtifactListener listener;
            private final Map<File, TransformFileOperation> fileResults;
            private final Transformer<List<File>, File> transform;

            TransformingAsyncArtifactListener(Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, BuildOperationQueue<RunnableBuildOperation> actions, Transformer<List<File>, File> transform, AsyncArtifactListener listener, Map<File, TransformFileOperation> fileResults) {
                this.artifactResults = artifactResults;
                this.actions = actions;
                this.transform = transform;
                this.listener = listener;
                this.fileResults = fileResults;
            }

            @Override
            public void artifactAvailable(ResolvableArtifact artifact) {
                TransformArtifactOperation operation = new TransformArtifactOperation(artifact, transform);
                artifactResults.put(artifact, operation);
                actions.add(operation);
            }

            @Override
            public boolean requireArtifactFiles() {
                // Always need the files, as we need to run the transform in order to calculate the output artifacts.
                return true;
            }

            @Override
            public boolean includeFileDependencies() {
                return listener.includeFileDependencies();
            }

            @Override
            public void fileAvailable(File file) {
                TransformFileOperation operation = new TransformFileOperation(file, transform);
                fileResults.put(file, operation);
                actions.add(operation);
            }
        }

        private class TransformingResult implements Completion {
            private final Completion result;
            private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
            private final Map<File, TransformFileOperation> fileResults;

            TransformingResult(Completion result, Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults) {
                this.result = result;
                this.artifactResults = artifactResults;
                this.fileResults = fileResults;
            }

            @Override
            public void visit(ArtifactVisitor visitor) {
                result.visit(new ArtifactTransformingVisitor(visitor, attributes, artifactResults, fileResults));
            }
        }
    }

    private static class TransformArtifactOperation implements RunnableBuildOperation {
        private final ResolvableArtifact artifact;
        private final Transformer<List<File>, File> transform;
        private Throwable failure;
        private List<File> result;

        TransformArtifactOperation(ResolvableArtifact artifact, Transformer<List<File>, File> transform) {
            this.artifact = artifact;
            this.transform = transform;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                result = transform.transform(artifact.getFile());
            } catch (Throwable t) {
                failure = t;
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
           return BuildOperationDescriptor.displayName("Apply " + transform + " to " + artifact);
        }
    }

    private static class TransformFileOperation implements RunnableBuildOperation {
        private final File file;
        private final Transformer<List<File>, File> transform;
        private Throwable failure;
        private List<File> result;

        TransformFileOperation(File file, Transformer<List<File>, File> transform) {
            this.file = file;
            this.transform = transform;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                result = transform.transform(file);
            } catch (Throwable t) {
                failure = t;
            }
        }
        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Apply " + transform + " to " + file);
        }
    }

    private static class ArtifactTransformingVisitor implements ArtifactVisitor {
        private final ArtifactVisitor visitor;
        private final AttributeContainerInternal target;
        private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
        private final Map<File, TransformFileOperation> fileResults;

        private ArtifactTransformingVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults) {
            this.visitor = visitor;
            this.target = target;
            this.artifactResults = artifactResults;
            this.fileResults = fileResults;
        }

        @Override
        public void visitArtifact(AttributeContainer variant, ResolvableArtifact artifact) {
            TransformArtifactOperation operation = artifactResults.get(artifact);
            if (operation.failure != null) {
                visitor.visitFailure(operation.failure);
                return;
            }

            List<File> transformedFiles = operation.result;

            TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();
            for (File output : transformedFiles) {
                ResolvedArtifact sourceArtifact = artifact.toPublicView();
                ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(sourceArtifact.getId().getComponentIdentifier(), output.getName());
                String extension = Files.getFileExtension(output.getName());
                IvyArtifactName artifactName = new DefaultIvyArtifactName(output.getName(), extension, extension);
                DefaultResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(sourceArtifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                visitor.visitArtifact(target, resolvedArtifact);
            }
        }

        @Override
        public void visitFailure(Throwable failure) {
            visitor.visitFailure(failure);
        }

        @Override
        public void visitResolutionFailure(ResolutionFailure<?> resolutionFailure) {
            visitor.visitResolutionFailure(resolutionFailure);
        }

        @Override
        public boolean includeFiles() {
            return visitor.includeFiles();
        }

        @Override
        public boolean requireArtifactFiles() {
            return visitor.requireArtifactFiles();
        }

        @Override
        public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
            TransformFileOperation operation = fileResults.get(file);
            if (operation.failure != null) {
                visitor.visitFailure(operation.failure);
                return;
            }

            List<File> result = operation.result;
            for (File outputFile : result) {
                visitor.visitFile(new ComponentFileArtifactIdentifier(artifactIdentifier.getComponentIdentifier(), outputFile.getName()), target, outputFile);
            }
        }
    }
}
