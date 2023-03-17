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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class DefaultTransformedVariantFactory implements TransformedVariantFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final TransformationNodeFactory transformationNodeFactory;
    private final ConcurrentMap<VariantKey, ResolvedArtifactSet> variants = new ConcurrentHashMap<>();
    private final Factory externalFactory = this::doCreateExternal;
    private final Factory projectFactory = this::doCreateProject;

    public DefaultTransformedVariantFactory(BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory, TransformationNodeFactory transformationNodeFactory) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.transformationNodeFactory = transformationNodeFactory;
    }

    @Override
    public ResolvedArtifactSet transformedExternalArtifacts(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        return locateOrCreate(externalFactory, componentIdentifier, sourceVariant, variantDefinition, dependenciesResolverFactory);
    }

    @Override
    public ResolvedArtifactSet transformedProjectArtifacts(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        return locateOrCreate(projectFactory, componentIdentifier, sourceVariant, variantDefinition, dependenciesResolverFactory);
    }

    private ResolvedArtifactSet locateOrCreate(Factory factory, ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        ImmutableAttributes target = variantDefinition.getTargetAttributes();
        Transformation transformation = variantDefinition.getTransformation();
        VariantResolveMetadata.Identifier identifier = sourceVariant.getIdentifier();
        if (identifier == null) {
            // An ad hoc variant, do not cache the result
            return factory.create(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolverFactory);
        }
        VariantKey variantKey;
        if (transformation.requiresDependencies()) {
            variantKey = new VariantWithUpstreamDependenciesKey(identifier, target, dependenciesResolverFactory);
        } else {
            variantKey = new VariantKey(identifier, target);
        }

        // Can't use computeIfAbsent() as the default implementation does not allow recursive updates
        ResolvedArtifactSet result = variants.get(variantKey);
        if (result == null) {
            ResolvedArtifactSet newResult = factory.create(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolverFactory);
            result = variants.putIfAbsent(variantKey, newResult);
            if (result == null) {
                result = newResult;
            }
        }
        return result;
    }

    private TransformedExternalArtifactSet doCreateExternal(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        return new TransformedExternalArtifactSet(componentIdentifier, sourceVariant.getArtifacts(), variantDefinition.getTargetAttributes(), sourceVariant.getCapabilities().getCapabilities(), variantDefinition.getTransformation(), dependenciesResolverFactory, calculatedValueContainerFactory);
    }

    private TransformedProjectArtifactSet doCreateProject(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        AttributeContainer sourceAttributes;
        ResolvedArtifactSet sourceArtifacts;
        VariantDefinition previous = variantDefinition.getPrevious();
        if (previous != null) {
            sourceAttributes = previous.getTargetAttributes();
            sourceArtifacts = transformedProjectArtifacts(componentIdentifier, sourceVariant, previous, dependenciesResolverFactory);
        } else {
            sourceAttributes = sourceVariant.getAttributes();
            sourceArtifacts = sourceVariant.getArtifacts();
        }
        ComponentVariantIdentifier targetComponentVariant = new ComponentVariantIdentifier(componentIdentifier, variantDefinition.getTargetAttributes(), sourceVariant.getCapabilities().getCapabilities());
        List<TransformationNode> transformationNodes = createTransformationNodes(sourceArtifacts, sourceAttributes, targetComponentVariant, variantDefinition, dependenciesResolverFactory);
        return new TransformedProjectArtifactSet(targetComponentVariant, transformationNodes);
    }

    private List<TransformationNode> createTransformationNodes(
        ResolvedArtifactSet sourceArtifacts,
        AttributeContainer sourceAttributes,
        ComponentVariantIdentifier targetComponentVariant,
        VariantDefinition variantDefinition,
        ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory
    ) {
        TransformUpstreamDependenciesResolver dependenciesResolver = dependenciesResolverFactory.create(targetComponentVariant.getComponentId(), variantDefinition.getTransformation());
        TransformationStep transformationStep = variantDefinition.getTransformationStep();

        ImmutableList.Builder<TransformationNode> builder = ImmutableList.builder();
        sourceArtifacts.visitTransformSources(new ResolvedArtifactSet.TransformSourceVisitor() {
            @Override
            public void visitArtifact(ResolvableArtifact artifact) {
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(transformationStep);
                TransformationNode transformationNode = transformationNodeFactory.createInitial(targetComponentVariant, sourceAttributes, transformationStep, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
                builder.add(transformationNode);
            }

            @Override
            public void visitTransform(TransformationNode source) {
                TransformUpstreamDependencies upstreamDependencies = dependenciesResolver.dependenciesFor(transformationStep);
                TransformationNode transformationNode = transformationNodeFactory.createChained(targetComponentVariant, sourceAttributes, transformationStep, source, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
                builder.add(transformationNode);
            }
        });
        return builder.build();
    }

    private interface Factory {
        ResolvedArtifactSet create(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory);
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
        private final ExtraExecutionGraphDependenciesResolverFactory dependencies;

        public VariantWithUpstreamDependenciesKey(VariantResolveMetadata.Identifier sourceVariant, ImmutableAttributes target, ExtraExecutionGraphDependenciesResolverFactory dependencies) {
            super(sourceVariant, target);
            this.dependencies = dependencies;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ dependencies.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            VariantWithUpstreamDependenciesKey other = (VariantWithUpstreamDependenciesKey) obj;
            return dependencies == other.dependencies;
        }
    }
}
