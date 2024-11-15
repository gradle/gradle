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

import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultGraphBuilder;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.model.CalculatedValue;

import javax.annotation.Nullable;
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
    private final AttributeDesugaring attributeDesugaring;

    public DefaultConfigurationResolver(
        RepositoriesSupplier repositoriesSupplier,
        ShortCircuitingResolutionExecutor resolutionExecutor,
        AttributeDesugaring attributeDesugaring
    ) {
        this.repositoriesSupplier = repositoriesSupplier;
        this.resolutionExecutor = resolutionExecutor;
        this.attributeDesugaring = attributeDesugaring;
    }

    @Override
    public ResolverResults resolveBuildDependencies(ConfigurationInternal configuration, CalculatedValue<ResolverResults> futureCompleteResults) {
        RootComponentMetadataBuilder.RootComponentState rootComponent = configuration.toRootComponent();

        VisitedGraphResults missingConfigurationResults =
            maybeGetEmptyGraphForInvalidMissingConfigurationWithNoDependencies(configuration, rootComponent.getRootComponent());
        if (missingConfigurationResults != null) {
            return DefaultResolverResults.buildDependenciesResolved(missingConfigurationResults, ShortCircuitingResolutionExecutor.EmptyResults.INSTANCE,
                DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved(ShortCircuitingResolutionExecutor.EmptyResults.INSTANCE)
            );
        }

        return resolutionExecutor.resolveBuildDependencies(configuration, futureCompleteResults);
    }

    @Override
    public ResolverResults resolveGraph(ConfigurationInternal configuration) {

        RootComponentMetadataBuilder.RootComponentState rootComponent = configuration.toRootComponent();
        VisitedGraphResults missingConfigurationResults =
            maybeGetEmptyGraphForInvalidMissingConfigurationWithNoDependencies(configuration, rootComponent.getRootComponent());
        if (missingConfigurationResults != null) {
            ResolvedConfiguration resolvedConfiguration = new DefaultResolvedConfiguration(
                missingConfigurationResults, configuration.getResolutionHost(), ShortCircuitingResolutionExecutor.EmptyResults.INSTANCE, new ShortCircuitingResolutionExecutor.EmptyLenientConfiguration()
            );
            return DefaultResolverResults.graphResolved(missingConfigurationResults, ShortCircuitingResolutionExecutor.EmptyResults.INSTANCE,
                DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(
                    ShortCircuitingResolutionExecutor.EmptyResults.INSTANCE, resolvedConfiguration
                )
            );
        }

        AttributeContainerInternal attributes = configuration.toRootComponent().getRootVariant().getAttributes();
        List<ResolutionAwareRepository> filteredRepositories = repositoriesSupplier.get().stream()
            .filter(repository -> !shouldSkipRepository(repository, configuration.getName(), attributes))
            .collect(Collectors.toList());

        return resolutionExecutor.resolveGraph(configuration, filteredRepositories);
    }

    @Override
    public List<ResolutionAwareRepository> getAllRepositories() {
        return repositoriesSupplier.get();
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

    /**
     * Verifies if the configuration has been removed from the container before it was resolved.
     * This fails and has failed in the past, but only when the configuration has dependencies.
     * The short-circuiting done in {@link ShortCircuitingResolutionExecutor} made us not fail
     * when we should have. So, detect that case and deprecate it. This should be removed in 9.0,
     * and we will fail later on without any extra logic when calling
     * {@link RootComponentMetadataBuilder.RootComponentState#getRootVariant()}
     */
    @Nullable
    private VisitedGraphResults maybeGetEmptyGraphForInvalidMissingConfigurationWithNoDependencies(
        ConfigurationInternal configuration,
        LocalComponentGraphResolveState rootComponent
    ) {
        // This variant can be null if the configuration was removed from the container before resolution.
        @SuppressWarnings("deprecation") LocalVariantGraphResolveState rootVariant =
            rootComponent.getConfigurationLegacy(configuration.getName());
        if (rootVariant == null) {
            configuration.runDependencyActions();
            if (configuration.getAllDependencies().isEmpty()) {
                DeprecationLogger.deprecateBehaviour("Removing a configuration from the container before resolution")
                    .withAdvice("Do not remove configurations from the container and resolve them after.")
                    .willBecomeAnErrorInGradle9()
                    .undocumented()
                    .nagUser();

                MinimalResolutionResult emptyResult = ResolutionResultGraphBuilder.empty(
                    rootComponent.getModuleVersionId(),
                    rootComponent.getId(),
                    configuration.getAttributes().asImmutable(),
                    ImmutableCapabilities.of(rootComponent.getDefaultCapability()),
                    configuration.getName(),
                    attributeDesugaring
                );

                return new DefaultVisitedGraphResults(emptyResult, Collections.emptySet(), null);
            }
        }

        return null;
    }
}
