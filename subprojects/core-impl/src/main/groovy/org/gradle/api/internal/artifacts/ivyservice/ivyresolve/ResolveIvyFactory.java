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
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.ivyservice.IvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;

public class ResolveIvyFactory {
    private final IvyFactory ivyFactory;
    private final ResolverProvider resolverProvider;
    private final SettingsConverter settingsConverter;
    private final ArtifactResolutionCache artifactResolutionCache;

    public ResolveIvyFactory(IvyFactory ivyFactory, ResolverProvider resolverProvider, SettingsConverter settingsConverter, ArtifactResolutionCache artifactResolutionCache) {
        this.ivyFactory = ivyFactory;
        this.resolverProvider = resolverProvider;
        this.settingsConverter = settingsConverter;
        this.artifactResolutionCache = artifactResolutionCache;
    }

    public IvyAdapter create(ResolutionStrategyInternal resolutionStrategy) {
        IvySettings ivySettings = settingsConverter.convertForResolve(resolverProvider.getResolvers(), resolutionStrategy);
        Ivy ivy = ivyFactory.createIvy(ivySettings);
        return new DefaultIvyAdapter(ivy, artifactResolutionCache, resolutionStrategy);
    }
}
