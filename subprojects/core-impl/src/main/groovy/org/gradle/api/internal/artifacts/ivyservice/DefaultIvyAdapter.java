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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.util.WrapUtil;

public class DefaultIvyAdapter implements IvyAdapter {
    private static final String TOP_LEVEL_RESOLVER_CHAIN_NAME = "topLevelResolverChain";
    private static final String CLIENT_MODULE_RESOLVER_NAME = "clientModuleResolver";

    private final Ivy ivy;
    private final DependencyResolver primaryResolver;
    private final VersionMatcher versionMatcher;
    private final ResolutionStrategyInternal resolutionStrategy;

    public DefaultIvyAdapter(Ivy ivy, DependencyResolver internalRepository, ClientModuleRegistry clientModuleRegistry, ResolutionStrategyInternal resolutionStrategy) {
        this.ivy = ivy;
        DependencyResolver userResolver = ivy.getSettings().getDefaultResolver();
        primaryResolver = constructPrimaryResolver(clientModuleRegistry, internalRepository, userResolver);
        versionMatcher = ivy.getSettings().getVersionMatcher();
        this.resolutionStrategy = resolutionStrategy;
    }
    
    private DependencyResolver constructPrimaryResolver(ClientModuleRegistry clientModuleRegistry, DependencyResolver internalResolver, DependencyResolver userResolver) {
        ClientModuleResolver clientModuleResolver = new ClientModuleResolver(CLIENT_MODULE_RESOLVER_NAME, clientModuleRegistry, primaryResolver);
        PrimaryResolverChain primaryResolverChain = new PrimaryResolverChain(clientModuleResolver, internalResolver, userResolver);
        primaryResolverChain.setName(TOP_LEVEL_RESOLVER_CHAIN_NAME);
        return primaryResolverChain;
    }

    public ResolveData getResolveData(String configurationName) {
        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configurationName));
        return new ResolveData(ivy.getResolveEngine(), options);
    }

    public DependencyToModuleResolver getDependencyToModuleResolver(ResolveData resolveData) {
        IvyResolverBackedDependencyToModuleResolver ivyBackedResolver = new IvyResolverBackedDependencyToModuleResolver(ivy, resolveData, primaryResolver, versionMatcher);
        return new VersionForcingDependencyToModuleResolver(ivyBackedResolver, this.resolutionStrategy.getForcedModules());
    }

    public ArtifactToFileResolver getArtifactToFileResolver() {
        return new IvyResolverBackedArtifactToFileResolver(primaryResolver);
    }
}
