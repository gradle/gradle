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

import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates {@link ComponentResolvers} for a given resolve context. The resolvers
 * will be able to resolve local components and components from the given
 * repositories.
 */
public class ComponentResolversFactory {
    private final List<ResolverProviderFactory> resolverFactories;
    private final ResolveIvyFactory moduleDependencyResolverFactory;
    private final ProjectDependencyResolver projectDependencyResolver;
    private final ImmutableAttributesFactory attributesFactory;
    private final ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final LocalComponentRegistry localComponentRegistry;

    @Inject
    public ComponentResolversFactory(
        List<ResolverProviderFactory> resolverFactories,
        ResolveIvyFactory moduleDependencyResolverFactory,
        ProjectDependencyResolver projectDependencyResolver,
        ImmutableAttributesFactory attributesFactory,
        ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
        GlobalDependencyResolutionRules metadataHandler,
        LocalComponentRegistry localComponentRegistry
    ) {
        this.resolverFactories = resolverFactories;
        this.moduleDependencyResolverFactory = moduleDependencyResolverFactory;
        this.projectDependencyResolver = projectDependencyResolver;
        this.attributesFactory = attributesFactory;
        this.componentMetadataSupplierRuleExecutor = componentMetadataSupplierRuleExecutor;
        this.metadataHandler = metadataHandler;
        this.localComponentRegistry = localComponentRegistry;
    }

    public ComponentResolvers create(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema
    ) {
        List<ComponentResolvers> resolvers = new ArrayList<>(3);
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolvers, localComponentRegistry);
        }
        resolvers.add(projectDependencyResolver);

        // We should avoid using `resolveContext` if possible here.
        // We should not need to know _what_ we're resolving in order to construct a resolver for a set of repositories.
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        AttributeContainerInternal requestedAttributes = resolveContext.getAttributes();

        resolvers.add(moduleDependencyResolverFactory.create(
            resolutionStrategy,
            repositories,
            metadataHandler.getComponentMetadataProcessorFactory(),
            // `requestedAttributes` and `consumerSchema` are used to support filtering components by attributes
            // when using dynamic versions. We should consider just removing that feature and making dynamic
            // version selection dumber. Needing to know _what_ is being resolved (the `requestedAttributes`)
            // should not be necessary to build a resolver that could theoretically resolve anything.
            requestedAttributes,
            consumerSchema,
            attributesFactory,
            componentMetadataSupplierRuleExecutor
        ));

        return new ComponentResolversChain(resolvers);
    }

}
