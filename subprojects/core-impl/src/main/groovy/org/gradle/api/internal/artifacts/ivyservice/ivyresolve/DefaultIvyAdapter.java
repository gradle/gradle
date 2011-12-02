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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactToFileResolver;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.gradle.util.WrapUtil;

class DefaultIvyAdapter implements IvyAdapter {
    private final Ivy ivy;
    private final VersionMatcher versionMatcher;
    private final UserResolverChain userResolver;
    private final ArtifactResolutionCache artifactResolutionCache;
    private final ResolutionStrategyInternal resolutionStrategy;

    public DefaultIvyAdapter(Ivy ivy, ArtifactResolutionCache artifactResolutionCache, ResolutionStrategyInternal resolutionStrategy) {
        this.ivy = ivy;
        this.artifactResolutionCache = artifactResolutionCache;
        this.resolutionStrategy = resolutionStrategy;
        userResolver = (UserResolverChain) ivy.getSettings().getResolver(SettingsConverter.USER_RESOLVER_CHAIN_NAME);
        versionMatcher = ivy.getSettings().getVersionMatcher();
    }

    public ResolveData getResolveData(String configurationName) {
        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configurationName));
        return new ResolveData(ivy.getResolveEngine(), options);
    }

    public DependencyToModuleResolver getDependencyToModuleResolver(ResolveData resolveData) {
        return new IvyResolverBackedDependencyToModuleResolver(ivy, resolveData, userResolver, versionMatcher);
    }

    public ArtifactToFileResolver getArtifactToFileResolver() {
        return new IvyResolverBackedArtifactToFileResolver(userResolver, artifactResolutionCache, resolutionStrategy.getCachePolicy());
    }
}
