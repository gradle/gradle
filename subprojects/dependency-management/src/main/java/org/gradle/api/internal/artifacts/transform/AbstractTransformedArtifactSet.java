/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.EndCollection;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;

import java.util.List;

/**
 * Transformed artifact set that performs the transform itself when visited.
 */
public abstract class AbstractTransformedArtifactSet implements TransformedArtifactSet, FileCollectionInternal.Source {
    private final CalculatedValueContainer<ImmutableList<ResolvedArtifactSet.Artifacts>, CalculateArtifacts> result;

    public AbstractTransformedArtifactSet(
        ComponentIdentifier componentIdentifier,
        ResolvedArtifactSet delegate,
        ImmutableAttributes targetVariantAttributes,
        List<? extends Capability> capabilities,
        TransformChain transformChain,
        TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        TransformUpstreamDependenciesResolver dependenciesResolver = dependenciesResolverFactory.create(componentIdentifier, transformChain);
        ImmutableList.Builder<BoundTransformStep> builder = ImmutableList.builder();
        transformChain.visitTransformSteps(step -> builder.add(new BoundTransformStep(step, dependenciesResolver.dependenciesFor(step))));
        ImmutableList<BoundTransformStep> steps = builder.build();
        this.result = calculatedValueContainerFactory.create(Describables.of(componentIdentifier), new CalculateArtifacts(componentIdentifier, delegate, targetVariantAttributes, capabilities, steps));
    }

    public AbstractTransformedArtifactSet(CalculatedValueContainer<ImmutableList<ResolvedArtifactSet.Artifacts>, CalculateArtifacts> result) {
        this.result = result;
    }

    public CalculatedValueContainer<ImmutableList<Artifacts>, CalculateArtifacts> getResult() {
        return result;
    }

    @Override
    public void visit(Visitor visitor) {
        FileCollectionStructureVisitor.VisitType visitType = visitor.prepareForVisit(this);
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            visitor.visitArtifacts(new EndCollection(this));
            return;
        }

        // Calculate the artifacts now
        result.finalizeIfNotAlready();
        for (Artifacts artifacts : result.get()) {
            artifacts.prepareForVisitingIfNotAlready();
            visitor.visitArtifacts(artifacts);
        }
        // Need to fire an "end collection" event. Should clean this up so it is not necessary
        visitor.visitArtifacts(new EndCollection(this));
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        result.visitDependencies(context);
    }

    @Override
    public void visitTransformSources(TransformSourceVisitor visitor) {
        // Should never be called
        throw new IllegalStateException();
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        // Should never be called
        throw new IllegalStateException();
    }

    public static class CalculateArtifacts implements ValueCalculator<ImmutableList<Artifacts>> {
        private final ComponentIdentifier ownerId;
        private final ResolvedArtifactSet delegate;
        private final ImmutableList<BoundTransformStep> steps;
        private final ImmutableAttributes targetVariantAttributes;
        private final List<? extends Capability> capabilities;

        public CalculateArtifacts(ComponentIdentifier ownerId, ResolvedArtifactSet delegate, ImmutableAttributes targetVariantAttributes, List<? extends Capability> capabilities, ImmutableList<BoundTransformStep> steps) {
            this.ownerId = ownerId;
            this.delegate = delegate;
            this.steps = steps;
            this.targetVariantAttributes = targetVariantAttributes;
            this.capabilities = capabilities;
        }

        public ComponentIdentifier getOwnerId() {
            return ownerId;
        }

        public ResolvedArtifactSet getDelegate() {
            return delegate;
        }

        public ImmutableList<BoundTransformStep> getSteps() {
            return steps;
        }

        public ImmutableAttributes getTargetVariantAttributes() {
            return targetVariantAttributes;
        }

        public List<? extends Capability> getCapabilities() {
            return capabilities;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (BoundTransformStep step : steps) {
                context.add(step.getUpstreamDependencies());
            }
        }

        @Override
        public ImmutableList<Artifacts> calculateValue(NodeExecutionContext context) {
            // Isolate the transform parameters, if not already done
            for (BoundTransformStep step : steps) {
                step.getTransformStep().isolateParametersIfNotAlready();
                step.getUpstreamDependencies().finalizeIfNotAlready();
            }

            ImmutableList.Builder<Artifacts> builder = ImmutableList.builderWithExpectedSize(1);
            delegate.visit(new TransformingAsyncArtifactListener(steps, targetVariantAttributes, capabilities, builder));
            return builder.build();
        }
    }
}
