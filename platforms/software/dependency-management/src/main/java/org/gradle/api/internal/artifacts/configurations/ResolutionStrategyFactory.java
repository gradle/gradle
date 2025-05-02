/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ComponentSelectorNotationConverter;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCapabilitiesResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.internal.VcsResolver;

import javax.inject.Inject;

/**
 * Creates fully initialized {@link ResolutionStrategyInternal} instances.
 */
@ServiceScope(Scope.Project.class)
public class ResolutionStrategyFactory implements Factory<ResolutionStrategyInternal> {

    private final BuildState currentBuild;
    private final Instantiator instantiator;
    private final GlobalDependencyResolutionRules globalDependencySubstitutionRules;
    private final VcsResolver vcsResolver;
    private final AttributesFactory attributesFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final DependencyLockingProvider dependencyLockingProvider;
    private final ComponentSelectorNotationConverter moduleSelectorNotationParser;
    private final ObjectFactory objectFactory;
    private final StartParameter startParameter;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final NotationParser<Object, ComponentIdentifier> componentIdentifierNotationParser;

    @Inject
    public ResolutionStrategyFactory(
        BuildState currentBuild,
        Instantiator instantiator,
        GlobalDependencyResolutionRules globalDependencySubstitutionRules,
        VcsResolver vcsResolver,
        AttributesFactory attributesFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ComponentSelectorConverter componentSelectorConverter,
        DependencyLockingProvider dependencyLockingProvider,
        ComponentSelectorNotationConverter moduleSelectorNotationParser,
        ObjectFactory objectFactory,
        StartParameter startParameter
    ) {
        this.currentBuild = currentBuild;
        this.instantiator = instantiator;
        this.globalDependencySubstitutionRules = globalDependencySubstitutionRules;
        this.vcsResolver = vcsResolver;
        this.attributesFactory = attributesFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.componentSelectorConverter = componentSelectorConverter;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.moduleSelectorNotationParser = moduleSelectorNotationParser;
        this.objectFactory = objectFactory;
        this.startParameter = startParameter;
        this.capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        this.componentIdentifierNotationParser = new ComponentIdentifierParserFactory().create();
    }

    @Override
    public ResolutionStrategyInternal create() {
        CapabilitiesResolutionInternal capabilitiesResolutionInternal = instantiator.newInstance(
            DefaultCapabilitiesResolution.class,
            capabilityNotationParser,
            componentIdentifierNotationParser
        );

        DependencySubstitutionsInternal dependencySubstitutions = DefaultDependencySubstitutions.forResolutionStrategy(
            currentBuild, moduleSelectorNotationParser, instantiator, objectFactory, attributesFactory, capabilityNotationParser
        );

        CachePolicy cachePolicy = createCachePolicy(startParameter);

        return instantiator.newInstance(DefaultResolutionStrategy.class,
            cachePolicy,
            dependencySubstitutions,
            globalDependencySubstitutionRules,
            vcsResolver,
            moduleIdentifierFactory,
            componentSelectorConverter,
            dependencyLockingProvider,
            capabilitiesResolutionInternal,
            objectFactory
        );
    }

    private static CachePolicy createCachePolicy(StartParameter startParameter) {
        CachePolicy cachePolicy = new DefaultCachePolicy();
        if (startParameter.isOffline()) {
            cachePolicy.setOffline();
        } else if (startParameter.isRefreshDependencies()) {
            cachePolicy.setRefreshDependencies();
        }
        return cachePolicy;
    }
}
