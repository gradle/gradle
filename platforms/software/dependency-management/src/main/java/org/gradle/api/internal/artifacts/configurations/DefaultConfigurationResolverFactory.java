/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ComponentModuleMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.CachingDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters;
import org.gradle.api.internal.artifacts.ivyservice.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ShortCircuitingResolutionExecutor;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ConfigurationResolverFactory}.
 */
public class DefaultConfigurationResolverFactory implements ConfigurationResolverFactory {

    private final RepositoriesSupplier repositoriesSupplier;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final ShortCircuitingResolutionExecutor resolutionExecutor;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ComponentModuleMetadataHandlerInternal componentModuleMetadataHandler;
    private final AttributeSchemaServices attributeSchemaServices;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final AttributesFactory attributesFactory;
    private final BuildOperationRunner buildOperationRunner;

    @Inject
    public DefaultConfigurationResolverFactory(
        RepositoriesSupplier repositoriesSupplier,
        WorkerThreadRegistry workerThreadRegistry,
        ShortCircuitingResolutionExecutor resolutionExecutor,
        ArtifactTypeRegistry artifactTypeRegistry,
        ComponentModuleMetadataHandlerInternal componentModuleMetadataHandler,
        AttributeSchemaServices attributeSchemaServices,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        AttributesFactory attributesFactory,
        BuildOperationRunner buildOperationRunner
    ) {
        this.repositoriesSupplier = repositoriesSupplier;
        this.workerThreadRegistry = workerThreadRegistry;
        this.resolutionExecutor = resolutionExecutor;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.componentModuleMetadataHandler = componentModuleMetadataHandler;
        this.attributeSchemaServices = attributeSchemaServices;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.attributesFactory = attributesFactory;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public CachingDependencyResolver forConfiguration(ConfigurationInternal configuration, RootComponentMetadataBuilder rootComponentMetadataBuilder) {
        return new CachingDependencyResolver(
            new ConfigurationResolveContext(configuration, rootComponentMetadataBuilder),
            workerThreadRegistry,
            resolutionExecutor,
            calculatedValueContainerFactory
        );
    }

    private class ConfigurationResolveContext implements ResolveContext {

        private final ConfigurationInternal configuration;
        private final RootComponentMetadataBuilder rootComponentMetadataBuilder;

        public ConfigurationResolveContext(
            ConfigurationInternal configuration,
            RootComponentMetadataBuilder rootComponentMetadataBuilder
        ) {
            this.configuration = configuration;
            this.rootComponentMetadataBuilder = rootComponentMetadataBuilder;
        }

        @Override
        public String getDisplayName() {
            return configuration.getDisplayName();
        }

        @Override
        public ModelContainer<?> getModel() {
            return configuration.getDomainObjectContext().getModel();
        }

        @Override
        public LegacyResolutionParameters getLegacyResolutionParameters() {
            return new ConfigurationLegacyResolutionParameters(configuration.getResolutionStrategy());
        }

        @Override
        public ResolutionParameters getResolutionParameters(boolean forCompleteGraph) {
            RootComponentMetadataBuilder.RootComponentState root = rootComponentMetadataBuilder.toRootComponent(configuration);
            ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
            ImmutableList<ResolutionParameters.ModuleVersionLock> moduleVersionLocks = forCompleteGraph ? configuration.getConsistentResolutionVersionLocks() : ImmutableList.of();
            ImmutableArtifactTypeRegistry immutableArtifactTypeRegistry = attributeSchemaServices.getArtifactTypeRegistryFactory().create(artifactTypeRegistry);
            ImmutableModuleReplacements moduleReplacements = componentModuleMetadataHandler.getModuleReplacements();
            ConfigurationFailureResolutions failureResolutions = new ConfigurationFailureResolutions(configuration.getDomainObjectContext().getProjectIdentity(), configuration.getName());

            return new ResolutionParameters(
                configuration.getResolutionHost(),
                root.getRootComponent(),
                root.getRootVariant(),
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

        @Override
        public List<ResolutionAwareRepository> getRepositories() {
            AttributeContainerInternal attributes = configuration.getAttributes();
            return repositoriesSupplier.get().stream()
                .filter(repository -> !shouldSkipRepository(repository, configuration.getName(), attributes))
                .collect(Collectors.toList());
        }

        @Override
        public void beforeResolve() {
            configuration.runDependencyActions();
            configuration.getDependencyResolutionListeners().getSource().beforeResolve(configuration.getIncoming());
        }

        @Override
        public void afterResolve(ResolverResults results) {
            // Mark all affected configurations as observed
            configuration.markAsObserved(ConfigurationInternal.InternalState.GRAPH_RESOLVED);

            // TODO: Currently afterResolve runs if there are unresolved dependencies, which are
            //       resolution failures. However, they are not run for other failures.
            //       We should either _always_ run afterResolve, or only run it if _no_ failure occurred
            if (!results.getVisitedGraph().getResolutionFailure().isPresent()) {
                configuration.getDependencyResolutionListeners().getSource().afterResolve(configuration.getIncoming());
            }

            // We've notified beforeResolve and afterResolve listeners
            // Clear out the state as it's no longer needed
            configuration.getDependencyResolutionListeners().removeAll();

            // Discard State
            configuration.getResolutionStrategy().maybeDiscardStateRequiredForGraphResolution();
        }

        @Override
        public ResolverResults interceptResolution(
            LegacyResolutionParameters legacyParams,
            ResolutionParameters params,
            List<ResolutionAwareRepository> repositories,
            ResolutionAction resolution
        ) {
            return buildOperationRunner.call(new CallableBuildOperation<ResolverResults>() {
                @Override
                public ResolverResults call(BuildOperationContext context) {
                    ResolverResults results = resolution.proceed(legacyParams, params, repositories);

                    results.getVisitedGraph().getResolutionFailure().ifPresent(context::failed);

                    // When dependency resolution has failed, we don't want the build operation listeners to fail as well
                    // because:
                    // 1. the `failed` method will have been called with the user facing error
                    // 2. such an error may still lead to a valid dependency graph
                    MinimalResolutionResult resolutionResult = results.getVisitedGraph().getResolutionResult();
                    context.setResult(new ResolveConfigurationResolutionBuildOperationResult(
                        resolutionResult.getRootSource(),
                        resolutionResult.getRequestedAttributes(),
                        attributesFactory
                    ));

                    return results;
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    DomainObjectContext owner = configuration.getDomainObjectContext();

                    String displayName = "Resolve dependencies of " + owner.identityPath(configuration.getName());
                    ProjectIdentity projectId = owner.getProjectIdentity();
                    String projectPathString = null;
                    if (!owner.isScript()) {
                        if (projectId != null) {
                            projectPathString = projectId.getProjectPath().getPath();
                        }
                    }

                    return BuildOperationDescriptor.displayName(displayName)
                        .progressDisplayName(displayName)
                        .details(new ResolveConfigurationResolutionBuildOperationDetails(
                            configuration.getName(),
                            owner.isScript(),
                            configuration.getDescription(),
                            owner.getBuildPath().getPath(),
                            projectPathString,
                            configuration.isVisible(),
                            params.getRootVariant().getMetadata().isTransitive(),
                            repositories
                        ));
                }
            });
        }
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
        public CapabilitiesResolutionInternal getCapabilityConflictResolutionRules() {
            return resolutionStrategy.getCapabilitiesResolutionRules();
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
        public List<String> forVersionConflict(Set<Conflict> conflicts) {
            if (owningProject == null) {
                // owningProject is null for settings execution
                return Collections.emptyList();
            }

            String taskPath = owningProject.getBuildTreePath().append(Path.path("dependencyInsight")).getPath();

            ModuleVersionIdentifier identifier = conflicts.iterator().next().getVersions().get(0);
            String dependencyNotation = identifier.getGroup() + ":" + identifier.getName();

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
        String resolveContextName,
        AttributeContainer consumerAttributes
    ) {
        if (!(repository instanceof ContentFilteringRepository)) {
            return false;
        }

        ContentFilteringRepository cfr = (ContentFilteringRepository) repository;

        Set<String> includedConfigurations = cfr.getIncludedConfigurations();
        Set<String> excludedConfigurations = cfr.getExcludedConfigurations();

        if ((includedConfigurations != null && !includedConfigurations.contains(resolveContextName)) ||
            (excludedConfigurations != null && excludedConfigurations.contains(resolveContextName))
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

}
