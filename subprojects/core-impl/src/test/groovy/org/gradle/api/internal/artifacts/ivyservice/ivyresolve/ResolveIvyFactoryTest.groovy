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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.apache.ivy.Ivy
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.version.VersionMatcher
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.configurations.ResolverProvider
import org.gradle.api.internal.artifacts.ivyservice.IvyFactory
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache
import spock.lang.Specification

class ResolveIvyFactoryTest extends Specification {
    final IvyFactory ivyFactory = Mock()
    final ResolverProvider resolverProvider = Mock()
    final SettingsConverter settingsConverter = Mock()
    final ResolutionStrategyInternal resolutionStrategy = Mock()
    final ArtifactResolutionCache artifactResolutionCache = Mock()
    final ResolveIvyFactory factory = new ResolveIvyFactory(ivyFactory, resolverProvider, settingsConverter, artifactResolutionCache)

    def "creates Ivy instance"() {
        Ivy ivy = Mock()
        IvySettings ivySettings = Mock()
        DependencyResolver resolver1 = Mock()
        DependencyResolver resolver2 = Mock()
        UserResolverChain userResolverChain = Mock()
        VersionMatcher versionMatcher = Mock()

        when:
        def result = factory.create(resolutionStrategy)

        then:
        1 * resolverProvider.resolvers >> [resolver1, resolver2]
        1 * settingsConverter.convertForResolve([resolver1, resolver2], resolutionStrategy) >> ivySettings
        1 * ivyFactory.createIvy(ivySettings) >> ivy
        2 * ivy.getSettings() >> ivySettings
        1 * ivySettings.getResolver(SettingsConverter.USER_RESOLVER_CHAIN_NAME) >> userResolverChain
        1 * ivySettings.getVersionMatcher() >> versionMatcher
        0 * _._

        and:
        // TODO:DAZ more testing when this settles down
        result instanceof DefaultIvyAdapter
    }
}
