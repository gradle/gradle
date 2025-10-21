/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ComponentModuleMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.AdhocRootComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ProjectRootComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Responsible for resolving a configuration. Delegates to a {@link ShortCircuitingResolutionExecutor} to perform
 * the actual resolution.
 */
public class DefaultConfigurationResolver implements ConfigurationResolver {

    private final RepositoriesSupplier repositoriesSupplier;
    private final ShortCircuitingResolutionExecutor resolutionExecutor;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ComponentModuleMetadataHandlerInternal componentModuleMetadataHandler;
    private final AttributeSchemaServices attributeSchemaServices;
    private final LocalVariantGraphResolveStateBuilder variantStateBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final RootComponentProvider rootComponentProvider;

    public DefaultConfigurationResolver(
        RepositoriesSupplier repositoriesSupplier,
        ShortCircuitingResolutionExecutor resolutionExecutor,
        ArtifactTypeRegistry artifactTypeRegistry,
        ComponentModuleMetadataHandlerInternal componentModuleMetadataHandler,
        AttributeSchemaServices attributeSchemaServices,
        LocalVariantGraphResolveStateBuilder variantStateBuilder,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        RootComponentProvider rootComponentProvider
    ) {
        this.repositoriesSupplier = repositoriesSupplier;
        this.resolutionExecutor = resolutionExecutor;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.componentModuleMetadataHandler = componentModuleMetadataHandler;
        this.attributeSchemaServices = attributeSchemaServices;
        this.variantStateBuilder = variantStateBuilder;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.rootComponentProvider = rootComponentProvider;
    }

    @Override
    public ResolverResults resolveBuildDependencies(ConfigurationInternal configuration, CalculatedValue<ResolverResults> futureCompleteResults) {
        LocalComponentGraphResolveState rootComponent = rootComponentProvider.getRootComponent(configuration.isDetachedConfiguration());
        LocalVariantGraphResolveState rootVariant = asRootVariant(configuration, rootComponent.getId());

        ResolutionParameters params = getResolutionParameters(configuration, rootComponent, rootVariant, false);
        LegacyResolutionParameters legacyParams = new ConfigurationLegacyResolutionParameters(configuration.getResolutionStrategy());
        return resolutionExecutor.resolveBuildDependencies(legacyParams, params, futureCompleteResults);
    }

    @Override
    public ResolverResults resolveGraph(ConfigurationInternal configuration) {
        LocalComponentGraphResolveState rootComponent = rootComponentProvider.getRootComponent(configuration.isDetachedConfiguration());
        LocalVariantGraphResolveState rootVariant = asRootVariant(configuration, rootComponent.getId());

        AttributeContainerInternal attributes = rootVariant.getAttributes();
        List<ResolutionAwareRepository> filteredRepositories = repositoriesSupplier.get().stream()
            .filter(repository -> !shouldSkipRepository(repository, configuration.getName(), attributes))
            .collect(Collectors.toList());

        ResolutionParameters params = getResolutionParameters(configuration, rootComponent, rootVariant, true);
        LegacyResolutionParameters legacyParams = new ConfigurationLegacyResolutionParameters(configuration.getResolutionStrategy());
        return resolutionExecutor.resolveGraph(legacyParams, params, filteredRepositories);
    }

    private LocalVariantGraphResolveState asRootVariant(ConfigurationInternal configuration, ComponentIdentifier componentId) {
        return variantStateBuilder.createRootVariantState(
            configuration,
            componentId,
            new DefaultLocalVariantGraphResolveStateBuilder.DependencyCache(),
            configuration.getDomainObjectContext().getModel(),
            calculatedValueContainerFactory
        );
    }

    @Override
    public List<ResolutionAwareRepository> getAllRepositories() {
        return repositoriesSupplier.get();
    }

    private ResolutionParameters getResolutionParameters(
        ConfigurationInternal configuration,
        LocalComponentGraphResolveState rootComponent,
        LocalVariantGraphResolveState rootVariant,
        boolean includeConsistentResolutionLocks
    ) {
        ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
        ImmutableList<ResolutionParameters.ModuleVersionLock> moduleVersionLocks = includeConsistentResolutionLocks ? configuration.getConsistentResolutionVersionLocks() : ImmutableList.of();
        ImmutableArtifactTypeRegistry immutableArtifactTypeRegistry = attributeSchemaServices.getArtifactTypeRegistryFactory().create(artifactTypeRegistry);
        ImmutableModuleReplacements moduleReplacements = componentModuleMetadataHandler.getModuleReplacements();
        ConfigurationFailureResolutions failureResolutions = new ConfigurationFailureResolutions(configuration.getDomainObjectContext().getProjectIdentity(), configuration.getName());

        return new ResolutionParameters(
            configuration.getResolutionHost(),
            rootComponent,
            rootVariant,
            moduleVersionLocks,
            resolutionStrategy.getSortOrder(),
            configuration.getConfigurationIdentity(),
            immutableArtifactTypeRegistry,
            moduleReplacements,
            resolutionStrategy.getConflictResolution(),
            configuration.getName(),
            resolutionStrategy.isDependencyLockingEnabled(),
            resolutionStrategy.getIncludeAllSelectableVariantResults(),
            resolutionStrategy.isDependencyVerificationEnabled(),
            resolutionStrategy.isFailingOnDynamicVersions(),
            resolutionStrategy.isFailingOnChangingVersions(),
            failureResolutions,
            resolutionStrategy.getCachePolicy().asImmutable()
        );
    }

    private static class ConfigurationLegacyResolutionParameters implements LegacyResolutionParameters {

        private final ResolutionStrategyInternal resolutionStrategy;

        public ConfigurationLegacyResolutionParameters(ResolutionStrategyInternal resolutionStrategy) {
            this.resolutionStrategy = resolutionStrategy;
        }

        @Override
        public ImmutableActionSet<DependencySubstitutionInternal> getDependencySubstitutionRules() {
            return resolutionStrategy.getDependencySubstitutionRule();
        }

        @Override
        public ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> getCapabilityConflictResolutionRules() {
            return resolutionStrategy.getCapabilitiesResolutionRules().getRules();
        }

        @Override
        public ComponentSelectionRulesInternal getComponentSelectionRules() {
            return resolutionStrategy.getComponentSelection();
        }

    }

    private static class ConfigurationFailureResolutions implements ResolutionParameters.FailureResolutions {

        private final @Nullable ProjectIdentity owningProject;
        private final String configurationName;

        public ConfigurationFailureResolutions(
            @Nullable ProjectIdentity owningProject,
            String configurationName
        ) {
            this.owningProject = owningProject;
            this.configurationName = configurationName;
        }

        @Override
        public List<String> forVersionConflict(Conflict conflict) {
            if (owningProject == null) {
                // owningProject is null for settings execution
                return Collections.emptyList();
            }

            String taskPath = owningProject.getBuildTreePath().append(Path.path("dependencyInsight")).asString();

            ModuleIdentifier moduleId = conflict.getModuleId();
            String dependencyNotation = moduleId.getGroup() + ":" + moduleId.getName();

            return Collections.singletonList(String.format(
                "Run with %s --configuration %s --dependency %s to get more insight on how to solve the conflict.",
                taskPath, configurationName, dependencyNotation
            ));
        }

    }

    /**
     * Determines if the repository should not be used to resolve this configuration.
     */
    private static boolean shouldSkipRepository(
        ResolutionAwareRepository repository,
        String configurationName,
        AttributeContainer consumerAttributes
    ) {
        if (!(repository instanceof ContentFilteringRepository)) {
            return false;
        }

        ContentFilteringRepository cfr = (ContentFilteringRepository) repository;

        Set<String> includedConfigurations = cfr.getIncludedConfigurations();
        Set<String> excludedConfigurations = cfr.getExcludedConfigurations();

        if ((includedConfigurations != null && !includedConfigurations.contains(configurationName)) ||
            (excludedConfigurations != null && excludedConfigurations.contains(configurationName))
        ) {
            return true;
        }

        Map<Attribute<Object>, Set<Object>> requiredAttributes = cfr.getRequiredAttributes();
        return hasNonRequiredAttribute(requiredAttributes, consumerAttributes);
    }

    /**
     * Accepts a map of attribute types to the set of values that are allowed for that attribute type.
     * If the request attributes of the resolve context being resolved do not match the allowed values,
     * then the repository is skipped.
     */
    private static boolean hasNonRequiredAttribute(
        @Nullable Map<Attribute<Object>, Set<Object>> requiredAttributes,
        AttributeContainer consumerAttributes
    ) {
        if (requiredAttributes == null) {
            return false;
        }

        for (Map.Entry<Attribute<Object>, Set<Object>> entry : requiredAttributes.entrySet()) {
            Attribute<Object> key = entry.getKey();
            Set<Object> allowedValues = entry.getValue();
            Object value = consumerAttributes.getAttribute(key);
            if (!allowedValues.contains(value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Constructs new instances of {@link DefaultConfigurationResolver}s.
     */
    public static class Factory implements ConfigurationResolver.Factory {

        private final DependencyMetaDataProvider moduleIdentity;
        private final RepositoriesSupplier repositoriesSupplier;
        private final ShortCircuitingResolutionExecutor resolutionExecutor;
        private final ArtifactTypeRegistry artifactTypeRegistry;
        private final ComponentModuleMetadataHandlerInternal componentModuleMetadataHandler;
        private final AttributeSchemaServices attributeSchemaServices;
        private final LocalVariantGraphResolveStateBuilder variantStateBuilder;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final ImmutableAttributesSchemaFactory attributesSchemaFactory;
        private final LocalComponentGraphResolveStateFactory localResolveStateFactory;

        public Factory(
            DependencyMetaDataProvider moduleIdentity,
            RepositoriesSupplier repositoriesSupplier,
            ShortCircuitingResolutionExecutor resolutionExecutor,
            ArtifactTypeRegistry artifactTypeRegistry,
            ComponentModuleMetadataHandlerInternal componentModuleMetadataHandler,
            AttributeSchemaServices attributeSchemaServices,
            LocalVariantGraphResolveStateBuilder variantStateBuilder,
            CalculatedValueContainerFactory calculatedValueContainerFactory,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            ImmutableAttributesSchemaFactory attributesSchemaFactory,
            LocalComponentGraphResolveStateFactory localResolveStateFactory
        ) {
            this.moduleIdentity = moduleIdentity;
            this.repositoriesSupplier = repositoriesSupplier;
            this.resolutionExecutor = resolutionExecutor;
            this.artifactTypeRegistry = artifactTypeRegistry;
            this.componentModuleMetadataHandler = componentModuleMetadataHandler;
            this.attributeSchemaServices = attributeSchemaServices;
            this.variantStateBuilder = variantStateBuilder;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.attributesSchemaFactory = attributesSchemaFactory;
            this.localResolveStateFactory = localResolveStateFactory;
        }

        @Override
        public ConfigurationResolver create(
            ConfigurationsProvider configurations,
            DomainObjectContext owner,
            AttributesSchemaInternal schema
        ) {
            RootComponentProvider rootComponentProvider = createRootComponentProvider(configurations, owner, schema);

            return new DefaultConfigurationResolver(
                repositoriesSupplier,
                resolutionExecutor,
                artifactTypeRegistry,
                componentModuleMetadataHandler,
                attributeSchemaServices,
                variantStateBuilder,
                calculatedValueContainerFactory,
                rootComponentProvider
            );
        }

        private RootComponentProvider createRootComponentProvider(
            ConfigurationsProvider configurations,
            DomainObjectContext owner,
            AttributesSchemaInternal schema
        ) {
            AdhocRootComponentProvider adhocRootComponentProvider = new AdhocRootComponentProvider(
                schema,
                moduleIdentifierFactory,
                attributesSchemaFactory,
                localResolveStateFactory
            );

            if (owner.getProjectIdentity() == null) {
                return adhocRootComponentProvider;
            }

            // TODO #1629: Eventually, resolutions within a project should live within
            //  an adhoc root component, and should use an AdhocRootComponentProvider.
            return new ProjectRootComponentProvider(
                owner.getProject().getOwner(),
                moduleIdentity,
                schema,
                configurations,
                moduleIdentifierFactory,
                localResolveStateFactory,
                attributesSchemaFactory,
                adhocRootComponentProvider
            );
        }

    }

}
