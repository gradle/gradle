/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentResolversFactory;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ComponentResolversFactory}.
 */
public class DefaultComponentResolversFactory implements ComponentResolversFactory {
    private final List<ResolverProviderFactory> resolverFactories;
    private final ResolveIvyFactory moduleDependencyResolverFactory;
    private final ProjectDependencyResolver projectDependencyResolver;
    private final ImmutableAttributesFactory attributesFactory;
    private final ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor;
    private final GlobalDependencyResolutionRules metadataHandler;

    @Inject
    public DefaultComponentResolversFactory(
        List<ResolverProviderFactory> resolverFactories,
        ResolveIvyFactory moduleDependencyResolverFactory,
        ProjectDependencyResolver projectDependencyResolver,
        ImmutableAttributesFactory attributesFactory,
        ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
        GlobalDependencyResolutionRules metadataHandler
    ) {
        this.resolverFactories = resolverFactories;
        this.moduleDependencyResolverFactory = moduleDependencyResolverFactory;
        this.projectDependencyResolver = projectDependencyResolver;
        this.attributesFactory = attributesFactory;
        this.componentMetadataSupplierRuleExecutor = componentMetadataSupplierRuleExecutor;
        this.metadataHandler = metadataHandler;
    }

    @Override
    public ComponentResolvers create(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema
    ) {
        validateResolutionStrategy(resolveContext.getResolutionStrategy());

        List<ComponentResolvers> resolvers = new ArrayList<>(3);
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolvers);
        }
        resolvers.add(projectDependencyResolver);

        List<? extends ResolutionAwareRepository> filteredRepositories = repositories.stream()
            .filter(repository -> !shouldSkipRepository(repository, resolveContext.getName(), resolveContext.getAttributes()))
            .collect(Collectors.toList());

        resolvers.add(createModuleRepositoryResolvers(
            filteredRepositories,
            consumerSchema,
            // We should avoid using `resolveContext` if possible here.
            // We should not need to know _what_ we're resolving in order to construct a resolver for a set of repositories.
            // These parameters are used to support filtering components by attributes when using dynamic versions.
            // We should consider just removing that feature and making dynamic version selection dumber.
            resolveContext.getResolutionStrategy(),
            resolveContext.getAttributes(),
            metadataHandler
        ));

        return new ComponentResolversChain(resolvers);
    }

    private static void validateResolutionStrategy(ResolutionStrategyInternal resolutionStrategy) {
        if (resolutionStrategy.isDependencyLockingEnabled()) {
            if (resolutionStrategy.isFailingOnDynamicVersions()) {
                failOnDependencyLockingConflictingWith("fail on dynamic versions");
            } else if (resolutionStrategy.isFailingOnChangingVersions()) {
                failOnDependencyLockingConflictingWith("fail on changing versions");
            }
        }
    }

    private static void failOnDependencyLockingConflictingWith(String conflicting) {
        throw new InvalidUserCodeException("Resolution strategy has both dependency locking and " + conflicting + " enabled. You must choose between the two modes.");
    }

    /**
     * Creates a resolver that resolves module components from the given repositories.
     */
    private ComponentResolvers createModuleRepositoryResolvers(
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema,
        ResolutionStrategyInternal resolutionStrategy,
        AttributeContainerInternal requestedAttributes,
        GlobalDependencyResolutionRules metadataHandler
    ) {
        return moduleDependencyResolverFactory.create(
            resolutionStrategy,
            repositories,
            metadataHandler.getComponentMetadataProcessorFactory(),
            requestedAttributes,
            consumerSchema,
            attributesFactory,
            componentMetadataSupplierRuleExecutor
        );
    }

    /**
     * Determines if the repository should not be used to resolve the requested resolve context.
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
