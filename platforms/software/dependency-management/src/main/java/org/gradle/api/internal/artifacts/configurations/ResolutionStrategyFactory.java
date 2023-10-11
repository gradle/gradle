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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCapabilitiesResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.internal.VcsMappingsStore;

/**
 * Creates fully initialized {@link ResolutionStrategyInternal} instances.
 */
public class ResolutionStrategyFactory implements Factory<ResolutionStrategyInternal> {

    private final Instantiator instantiator;
    private final DependencySubstitutionRules globalDependencySubstitutionRules;
    private final VcsMappingsStore vcsMappingsStore;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final DependencyLockingProvider dependencyLockingProvider;
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final ObjectFactory objectFactory;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final NotationParser<Object, ComponentIdentifier> componentIdentifierNotationParser;

    public ResolutionStrategyFactory(
        Instantiator instantiator,
        DependencySubstitutionRules globalDependencySubstitutionRules,
        VcsMappingsStore vcsMappingsStore,
        ComponentIdentifierFactory componentIdentifierFactory,
        ImmutableAttributesFactory attributesFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ComponentSelectorConverter componentSelectorConverter,
        DependencyLockingProvider dependencyLockingProvider,
        NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
        ObjectFactory objectFactory,
        StartParameterResolutionOverride startParameterResolutionOverride
    ) {
        this.instantiator = instantiator;
        this.globalDependencySubstitutionRules = globalDependencySubstitutionRules;
        this.vcsMappingsStore = vcsMappingsStore;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.attributesFactory = attributesFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.componentSelectorConverter = componentSelectorConverter;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.moduleSelectorNotationParser = moduleSelectorNotationParser;
        this.objectFactory = objectFactory;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.capabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        this.componentIdentifierNotationParser = new ComponentIdentifierParserFactory().create();
    }

    @Override
    public ResolutionStrategyInternal create() {
        CapabilitiesResolutionInternal capabilitiesResolutionInternal = instantiator.newInstance(DefaultCapabilitiesResolution.class,
            capabilityNotationParser, componentIdentifierNotationParser
        );

        DependencySubstitutionsInternal dependencySubstitutions = DefaultDependencySubstitutions.forResolutionStrategy(
            componentIdentifierFactory, moduleSelectorNotationParser, instantiator, objectFactory, attributesFactory, capabilityNotationParser
        );

        ResolutionStrategyInternal resolutionStrategyInternal = instantiator.newInstance(DefaultResolutionStrategy.class,
            globalDependencySubstitutionRules,
            vcsMappingsStore,
            dependencySubstitutions,
            moduleIdentifierFactory,
            componentSelectorConverter,
            dependencyLockingProvider,
            capabilitiesResolutionInternal,
            objectFactory
        );

        startParameterResolutionOverride.applyToCachePolicy(resolutionStrategyInternal.getCachePolicy());

        return resolutionStrategyInternal;
    }
}
