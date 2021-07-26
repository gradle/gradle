/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains zero or more variants of a particular component.
 */
public abstract class DefaultArtifactSet implements ArtifactSet, ResolvedVariantSet, VariantSelector.Factory {
    private final ComponentIdentifier componentIdentifier;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributes selectionAttributes;

    private DefaultArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ImmutableAttributes selectionAttributes) {
        this.componentIdentifier = componentIdentifier;
        this.schema = schema;
        this.selectionAttributes = selectionAttributes;
    }

    @Override
    public ImmutableAttributes getOverriddenAttributes() {
        return selectionAttributes;
    }

    public static ArtifactSet createFromVariantMetadata(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, Set<? extends VariantResolveMetadata> variants, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes selectionAttributes, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        if (variants.size() == 1) {
            VariantResolveMetadata variantMetadata = variants.iterator().next();
            ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
            return new SingleVariantArtifactSet(componentIdentifier, schema, resolvedVariant, selectionAttributes);
        }
        ImmutableSet.Builder<ResolvedVariant> result = ImmutableSet.builder();
        for (VariantResolveMetadata variant : variants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
            result.add(resolvedVariant);
        }
        return new MultipleVariantArtifactSet(componentIdentifier, schema, result.build(), selectionAttributes);
    }

    public static ArtifactSet createFromVariants(ComponentIdentifier componentIdentifier, ImmutableSet<ResolvedVariant> variants, AttributesSchemaInternal schema, ImmutableAttributes selectionAttributes) {
        return new MultipleVariantArtifactSet(componentIdentifier, schema, variants, selectionAttributes);
    }

    public static ArtifactSet createForConfiguration(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, ConfigurationMetadata configuration, ImmutableList<? extends ComponentArtifactMetadata> artifacts, ModuleSources moduleSources, ExcludeSpec exclusions, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes selectionAttributes, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        VariantResolveMetadata variantMetadata = new DefaultVariantMetadata(configuration.getName(), new ComponentConfigurationIdentifier(componentIdentifier, configuration.getName()), configuration.asDescribable(), configuration.getAttributes(), artifacts, configuration.getCapabilities());
        ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
        return new SingleVariantArtifactSet(componentIdentifier, schema, resolvedVariant, selectionAttributes);
    }

    public static ArtifactSet adHocVariant(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, Collection<? extends ComponentArtifactMetadata> artifacts, ModuleSources moduleSources, ExcludeSpec exclusions, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes variantAttributes, ImmutableAttributes selectionAttributes, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        VariantResolveMetadata.Identifier identifier = null;
        if (artifacts.size() == 1) {
            identifier = new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId());
        }
        VariantResolveMetadata variantMetadata = new DefaultVariantMetadata(null, identifier, Describables.of(componentIdentifier), variantAttributes, ImmutableList.copyOf(artifacts), ImmutableCapabilities.EMPTY);
        ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry, calculatedValueContainerFactory);
        return new SingleVariantArtifactSet(componentIdentifier, schema, resolvedVariant, selectionAttributes);
    }

    private static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        // Apply any artifact type mappings to the attributes of the variant
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant.getAttributes().asImmutable(), variant.getArtifacts());
        return toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allResolvedArtifacts, attributes, calculatedValueContainerFactory);
    }

    public static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ImmutableAttributes variantAttributes, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        List<? extends ComponentArtifactMetadata> artifacts = variant.getArtifacts();
        ImmutableSet.Builder<ResolvableArtifact> resolvedArtifacts = ImmutableSet.builder();

        boolean hasExcludedArtifact = false;
        for (ComponentArtifactMetadata artifact : artifacts) {
            IvyArtifactName artifactName = artifact.getName();
            if (exclusions.excludesArtifact(ownerId.getModule(), artifactName)) {
                hasExcludedArtifact = true;
                continue;
            }

            ResolvableArtifact resolvedArtifact = allResolvedArtifacts.get(artifact.getId());
            if (resolvedArtifact == null) {
                ValueCalculator<File> artifactCalculator;
                if (artifactResolver instanceof ProjectArtifactResolver) {
                    artifactCalculator = ((ProjectArtifactResolver) artifactResolver).resolveArtifactLater(artifact);
                } else {
                    // TODO - push this up to all ArtifactResolver implementations
                    artifactCalculator = new LazyArtifactSupplier(artifact, moduleSources, artifactResolver);
                }
                CalculatedValue<File> artifactSource = calculatedValueContainerFactory.create(Describables.of(artifact.getId()), artifactCalculator);
                resolvedArtifact = new DefaultResolvableArtifact(ownerId, artifactName, artifact.getId(), context -> context.add(artifact.getBuildDependencies()), artifactSource, calculatedValueContainerFactory);
                allResolvedArtifacts.put(artifact.getId(), resolvedArtifact);
            }
            resolvedArtifacts.add(resolvedArtifact);
        }

        VariantResolveMetadata.Identifier identifier = variant.getIdentifier();
        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            identifier = null;
        }

        return ArtifactBackedResolvedVariant.create(identifier, variant.asDescribable(), variantAttributes, withImplicitCapability(variant, ownerId), resolvedArtifacts.build());
    }

    private static CapabilitiesMetadata withImplicitCapability(VariantResolveMetadata variant, ModuleVersionIdentifier identifier) {
        CapabilitiesMetadata capabilities = variant.getCapabilities();
        if (capabilities.getCapabilities().isEmpty()) {
            return ImmutableCapabilities.of(ImmutableCapability.defaultCapabilityForComponent(identifier));
        } else {
            return ImmutableCapabilities.copyAsImmutable(capabilities.getCapabilities());
        }
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public Describable asDescribable() {
        return Describables.of(componentIdentifier);
    }

    @Override
    public AttributesSchemaInternal getSchema() {
        return schema;
    }

    @Override
    public ResolvedArtifactSet asTransformed(ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolver, TransformedVariantFactory transformedVariantFactory) {
        if (componentIdentifier instanceof ProjectComponentIdentifier) {
            return transformedVariantFactory.transformedProjectArtifacts(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        } else {
            return transformedVariantFactory.transformedExternalArtifacts(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        }
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        if (!componentFilter.isSatisfiedBy(componentIdentifier)) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            return selector.select(this, this);
        }
    }

    private static class SingleVariantArtifactSet extends DefaultArtifactSet {
        private final ResolvedVariant variant;

        public SingleVariantArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ResolvedVariant variant, ImmutableAttributes selectionAttributes) {
            super(componentIdentifier, schema, selectionAttributes);
            this.variant = variant;
        }

        @Override
        public Set<ResolvedVariant> getVariants() {
            return ImmutableSet.of(variant);
        }
    }

    private static class MultipleVariantArtifactSet extends DefaultArtifactSet {
        private final Set<ResolvedVariant> variants;

        public MultipleVariantArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, Set<ResolvedVariant> variants, ImmutableAttributes selectionAttributes) {
            super(componentIdentifier, schema, selectionAttributes);
            this.variants = variants;
        }

        @Override
        public Set<ResolvedVariant> getVariants() {
            return variants;
        }
    }

    private static class SingleArtifactVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final ComponentArtifactIdentifier artifactIdentifier;

        public SingleArtifactVariantIdentifier(ComponentArtifactIdentifier artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public int hashCode() {
            return artifactIdentifier.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            SingleArtifactVariantIdentifier other = (SingleArtifactVariantIdentifier) obj;
            return artifactIdentifier.equals(other.artifactIdentifier);
        }
    }

    private static class LazyArtifactSupplier implements ValueCalculator<File> {
        private final ArtifactResolver artifactResolver;
        private final ModuleSources moduleSources;
        private final ComponentArtifactMetadata artifact;

        private LazyArtifactSupplier(ComponentArtifactMetadata artifact, ModuleSources moduleSources, ArtifactResolver artifactResolver) {
            this.artifact = artifact;
            this.artifactResolver = artifactResolver;
            this.moduleSources = moduleSources;
        }

        @Override
        public boolean usesMutableProjectState() {
            return false;
        }

        @Override
        @Nullable
        public ProjectInternal getOwningProject() {
            return null;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        public File calculateValue(NodeExecutionContext context) {
            DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifact, moduleSources, result);
            return result.getResult();
        }
    }
}
