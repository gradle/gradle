/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.AbstractFailedResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationRunner;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class DefaultTransformedVariantFactory implements TransformedVariantFactory {
    private final BuildOperationRunner buildOperationRunner;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final TransformStepNodeFactory transformStepNodeFactory;
    private final SharedTransformedVariantCache sharedTransformedVariantCache;
    private final StartParameterInternal startParameter;
    private final ConcurrentMap<VariantKey, ResolvedArtifactSet> variants = new ConcurrentHashMap<>();

    public DefaultTransformedVariantFactory(
        BuildOperationRunner buildOperationRunner,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        TransformStepNodeFactory transformStepNodeFactory,
        SharedTransformedVariantCache sharedTransformedVariantCache,
        StartParameterInternal startParameter
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.transformStepNodeFactory = transformStepNodeFactory;
        this.sharedTransformedVariantCache = sharedTransformedVariantCache;
        this.startParameter = startParameter;
    }

    @Override
    public ResolvedArtifactSet transformedExternalArtifacts(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, TransformUpstreamDependenciesResolver dependenciesResolver) {
        if (startParameter.isArtifactTransformsPerScopeVariantCache()) {
            // Temporary opt-out restoring the legacy per-scope caching, see StartParameterBuildOptions.ArtifactTransformsPerScopeVariantCacheOption
            return locateOrCreate(this::doCreateExternal, componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        }
        VariantResolveMetadata.Identifier identifier = sourceVariant.getIdentifier();
        if (identifier == null) {
            // An ad hoc variant, do not cache the result
            return doCreateExternal(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        }

        ResolvedArtifactSet sourceArtifacts = sourceVariant.getArtifacts();
        if (sourceArtifacts instanceof AbstractFailedResolvedArtifactSet) {
            // A failed set carries the scope-specific failure and visits no artifacts to key on, do not share it
            return locateOrCreate(
                (a, b, c, d) -> doCreateExternal(a, b, sourceArtifacts, c, d),
                componentIdentifier,
                sourceVariant,
                variantDefinition,
                dependenciesResolver);
        }
        List<ResolvableArtifact> visitedArtifacts = new ArrayList<>();
        sourceArtifacts.visitExternalArtifacts(visitedArtifacts::add);
        SharedTransformedVariantCache.ArtifactIdentities artifactIdentities = new SharedTransformedVariantCache.ArtifactIdentities(visitedArtifacts);

        ImmutableAttributes target = variantDefinition.getTargetAttributes();
        TransformChain transformChain = variantDefinition.getTransformChain();

        List<TransformStep> steps = new ArrayList<>();
        transformChain.visitTransformSteps(steps::add);
        for (TransformStep step : steps) {
            if (hasTaskProducedParameters(step.getTransform())) {
                // Parameter values are not final until those tasks have executed, so they must not be isolated
                // at resolution time. Keep the legacy per-scope caching and lazy isolation for such chains.
                return locateOrCreate(
                    (a, b, c, d) -> doCreateExternal(a, b, sourceArtifacts, c, d),
                    componentIdentifier,
                    sourceVariant,
                    variantDefinition,
                    dependenciesResolver);
            }
        }

        // Isolate the parameters of every step now, so that the content-based chain token can be computed.
        // This is the same isolation that would otherwise happen on first execution.
        ImmutableList.Builder<SharedTransformedVariantCache.TransformStepToken> chainTokenBuilder = ImmutableList.builder();
        for (TransformStep step : steps) {
            step.isolateParametersIfNotAlready();
            chainTokenBuilder.add(new SharedTransformedVariantCache.TransformStepToken(step.getTransform()));
        }
        ImmutableList<SharedTransformedVariantCache.TransformStepToken> chainToken = chainTokenBuilder.build();

        SharedTransformedVariantCache.SharedVariantKey variantKey;
        if (transformChain.requiresDependencies()) {
            // The dependencies resolver is per resolution scope, so these keys never match across scopes
            variantKey = new SharedTransformedVariantCache.SharedVariantWithUpstreamDependenciesKey(componentIdentifier, identifier, artifactIdentities, target, chainToken, dependenciesResolver);
        } else {
            variantKey = new SharedTransformedVariantCache.SharedVariantKey(componentIdentifier, identifier, artifactIdentities, target, chainToken);
        }

        // Can't use computeIfAbsent() as the default implementation does not allow recursive updates
        ResolvedArtifactSet result = sharedTransformedVariantCache.get(variantKey);
        if (result == null) {
            ResolvedArtifactSet newResult = doCreateExternal(componentIdentifier, sourceVariant, sourceArtifacts, variantDefinition, dependenciesResolver);
            result = sharedTransformedVariantCache.putIfAbsent(variantKey, newResult);
            if (result == null) {
                result = newResult;
            }
        }
        return result;
    }

    @Override
    public ResolvedArtifactSet transformedProjectArtifacts(
        ComponentIdentifier componentIdentifier,
        ResolvedVariant sourceVariant,
        VariantDefinition variantDefinition,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        return locateOrCreate(this::doCreateProject, componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
    }

    private ResolvedArtifactSet locateOrCreate(
        Factory factory,
        ComponentIdentifier componentIdentifier,
        ResolvedVariant sourceVariant,
        VariantDefinition variantDefinition,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        ImmutableAttributes target = variantDefinition.getTargetAttributes();
        TransformChain transformChain = variantDefinition.getTransformChain();
        VariantResolveMetadata.Identifier identifier = sourceVariant.getIdentifier();
        if (identifier == null) {
            // An ad hoc variant, do not cache the result
            return factory.create(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        }
        VariantKey variantKey;
        if (transformChain.requiresDependencies()) {
            variantKey = new VariantWithUpstreamDependenciesKey(identifier, target, dependenciesResolver);
        } else {
            variantKey = new VariantKey(identifier, target);
        }

        // Can't use computeIfAbsent() as the default implementation does not allow recursive updates
        ResolvedArtifactSet result = variants.get(variantKey);
        if (result == null) {
            ResolvedArtifactSet newResult = factory.create(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
            result = variants.putIfAbsent(variantKey, newResult);
            if (result == null) {
                result = newResult;
            }
        }
        return result;
    }

    private TransformedExternalArtifactSet doCreateExternal(
        ComponentIdentifier componentIdentifier,
        ResolvedVariant sourceVariant,
        VariantDefinition variantDefinition,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        return new TransformedExternalArtifactSet(
            componentIdentifier,
            sourceVariant.getSourceVariantId(),
            sourceVariant.getArtifacts(),
            variantDefinition.getTargetAttributes(),
            sourceVariant.getCapabilities(),
            variantDefinition.getTransformChain(),
            dependenciesResolver,
            calculatedValueContainerFactory
        );
    }

    private TransformedExternalArtifactSet doCreateExternal(
        ComponentIdentifier componentIdentifier,
        ResolvedVariant sourceVariant,
        ResolvedArtifactSet artifacts,
        VariantDefinition variantDefinition,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        return new TransformedExternalArtifactSet(
            componentIdentifier,
            sourceVariant.getSourceVariantId(),
            artifacts,
            variantDefinition.getTargetAttributes(),
            sourceVariant.getCapabilities(),
            variantDefinition.getTransformChain(),
            dependenciesResolver,
            calculatedValueContainerFactory
        );
    }

    private TransformedProjectArtifactSet doCreateProject(
        ComponentIdentifier componentIdentifier,
        ResolvedVariant sourceVariant,
        VariantDefinition variantDefinition,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        AttributeContainer sourceAttributes;
        ResolvedArtifactSet sourceArtifacts;
        VariantDefinition previous = variantDefinition.getPrevious();
        if (previous != null) {
            sourceAttributes = previous.getTargetAttributes();
            sourceArtifacts = transformedProjectArtifacts(componentIdentifier, sourceVariant, previous, dependenciesResolver);
        } else {
            sourceAttributes = sourceVariant.getAttributes();
            sourceArtifacts = sourceVariant.getArtifacts();
        }
        ComponentVariantIdentifier targetComponentVariant = new ComponentVariantIdentifier(componentIdentifier, variantDefinition.getTargetAttributes(), sourceVariant.getCapabilities());
        List<TransformStepNode> transformStepNodes = createTransformStepNodes(sourceArtifacts, sourceAttributes, targetComponentVariant, variantDefinition, dependenciesResolver);
        return new TransformedProjectArtifactSet(sourceVariant.getSourceVariantId(), targetComponentVariant, transformStepNodes);
    }

    private List<TransformStepNode> createTransformStepNodes(
        ResolvedArtifactSet sourceArtifacts,
        AttributeContainer sourceAttributes,
        ComponentVariantIdentifier targetComponentVariant,
        VariantDefinition variantDefinition,
        TransformUpstreamDependenciesResolver dependenciesResolver
    ) {
        ComponentIdentifier componentId = targetComponentVariant.getComponentId();
        TransformStep transformStep = variantDefinition.getTransformStep();

        ImmutableList.Builder<TransformStepNode> builder = ImmutableList.builder();
        sourceArtifacts.visitTransformSources(new ResolvedArtifactSet.TransformSourceVisitor() {
            @Override
            public void visitArtifact(ResolvableArtifact artifact) {
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(componentId, transformStep);
                TransformStepNode transformStepNode = transformStepNodeFactory.createInitial(targetComponentVariant, sourceAttributes, transformStep, artifact, upstreamDependencies, buildOperationRunner, calculatedValueContainerFactory);
                builder.add(transformStepNode);
            }

            @Override
            public void visitTransform(TransformStepNode source) {
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(componentId, transformStep);
                TransformStepNode transformStepNode = transformStepNodeFactory.createChained(targetComponentVariant, sourceAttributes, transformStep, source, upstreamDependencies, buildOperationRunner, calculatedValueContainerFactory);
                builder.add(transformStepNode);
            }
        });
        return builder.build();
    }

    private interface Factory {
        ResolvedArtifactSet create(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, TransformUpstreamDependenciesResolver dependenciesResolver);
    }

    /**
     * Whether the transform's parameters include input files produced by tasks, in which case their values
     * are not final until those tasks have executed and must not be isolated during dependency resolution.
     */
    private static boolean hasTaskProducedParameters(Transform transform) {
        TaskDependencyDetector detector = new TaskDependencyDetector();
        transform.visitDependencies(detector);
        return detector.hasTaskDependencies();
    }

    private static class TaskDependencyDetector extends AbstractTaskDependencyResolveContext {
        private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean found;

        boolean hasTaskDependencies() {
            return found;
        }

        @Override
        public void add(Object dependency) {
            if (found || !seen.add(dependency) || dependency == TaskDependencyContainer.EMPTY) {
                return;
            }
            if (dependency instanceof Task) {
                found = true;
            } else if (dependency instanceof TaskDependencyContainer) {
                ((TaskDependencyContainer) dependency).visitDependencies(this);
            } else if (dependency instanceof WorkNodeAction) {
                ((WorkNodeAction) dependency).visitDependencies(this);
            } else if (dependency instanceof Buildable) {
                TaskDependency buildDependencies = ((Buildable) dependency).getBuildDependencies();
                if (buildDependencies instanceof TaskDependencyContainer) {
                    ((TaskDependencyContainer) buildDependencies).visitDependencies(this);
                } else {
                    found = true;
                }
            } else {
                // Unknown dependency node kind, assume it can be produced at execution time
                found = true;
            }
        }
    }

    private static class VariantKey {
        private final VariantResolveMetadata.Identifier sourceVariant;
        private final ImmutableAttributes target;

        public VariantKey(VariantResolveMetadata.Identifier sourceVariant, ImmutableAttributes target) {
            this.sourceVariant = sourceVariant;
            this.target = target;
        }

        @Override
        public int hashCode() {
            return sourceVariant.hashCode() ^ target.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            VariantKey other = (VariantKey) obj;
            return sourceVariant.equals(other.sourceVariant) && target.equals(other.target);
        }
    }

    private static class VariantWithUpstreamDependenciesKey extends VariantKey {
        private final TransformUpstreamDependenciesResolver dependenciesResolver;

        public VariantWithUpstreamDependenciesKey(VariantResolveMetadata.Identifier sourceVariant, ImmutableAttributes target, TransformUpstreamDependenciesResolver dependenciesResolver) {
            super(sourceVariant, target);
            this.dependenciesResolver = dependenciesResolver;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ dependenciesResolver.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            VariantWithUpstreamDependenciesKey other = (VariantWithUpstreamDependenciesKey) obj;
            return dependenciesResolver.equals(other.dependenciesResolver);
        }
    }
}
