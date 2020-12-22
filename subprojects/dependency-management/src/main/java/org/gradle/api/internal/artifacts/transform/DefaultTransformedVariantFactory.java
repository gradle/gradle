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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class DefaultTransformedVariantFactory implements TransformedVariantFactory {
    private final TransformationNodeFactory transformationNodeFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ConcurrentMap<VariantKey, ResolvedArtifactSet> variants = new ConcurrentHashMap<>();
    private final Factory externalFactory = this::doCreateExternal;
    private final Factory projectFactory = this::doCreateProject;

    public DefaultTransformedVariantFactory(BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.transformationNodeFactory = new DefaultTransformationNodeFactory(buildOperationExecutor, calculatedValueContainerFactory);
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
        return new TransformedExternalArtifactSet(componentIdentifier, sourceVariant.getArtifacts(), variantDefinition.getTargetAttributes(), variantDefinition.getTransformation(), dependenciesResolverFactory, calculatedValueContainerFactory);
    }

    private TransformedProjectArtifactSet doCreateProject(ComponentIdentifier componentIdentifier, ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory) {
        ResolvedArtifactSet sourceArtifacts;
        if (variantDefinition.getSourceVariant() != null) {
            sourceArtifacts = transformedProjectArtifacts(componentIdentifier, sourceVariant, variantDefinition.getSourceVariant(), dependenciesResolverFactory);
        } else {
            sourceArtifacts = sourceVariant.getArtifacts();
        }
        return new TransformedProjectArtifactSet(componentIdentifier, sourceArtifacts, variantDefinition, dependenciesResolverFactory, transformationNodeFactory);
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
