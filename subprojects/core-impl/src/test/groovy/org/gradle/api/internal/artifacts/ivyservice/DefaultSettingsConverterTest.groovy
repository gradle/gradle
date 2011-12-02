/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.internal.Factory
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache
import org.gradle.api.internal.artifacts.ivyservice.filestore.FileStore
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LoopbackDependencyResolver
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserResolverChain
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DefaultSettingsConverterTest extends Specification {
    final IBiblioResolver testResolver = new IBiblioResolver()
    final IBiblioResolver testResolver2 = new IBiblioResolver()

    ResolutionStrategyInternal resolutionStrategy = Mock()
    ModuleResolutionCache dynamicRevisionCache = Mock()
    ModuleDescriptorCache moduleDescriptorCache = Mock()
    CacheLockingManager cacheLockingManager = Mock()
    FileStore fileStore = Mock()

    File testGradleUserHome = new File('gradleUserHome')

    final Factory<IvySettings> ivySettingsFactory = Mock()
    final IvySettings ivySettings = new IvySettings()

    DefaultSettingsConverter converter = new DefaultSettingsConverter(Mock(ProgressLoggerFactory), ivySettingsFactory, dynamicRevisionCache, moduleDescriptorCache, cacheLockingManager)

    public void setup() {
        testResolver.name = 'resolver'
    }

    public void testConvertForResolve() {
        when:
        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], resolutionStrategy)

        then:
        1 * ivySettingsFactory.create() >> ivySettings
        1 * resolutionStrategy.getCachePolicy()
        1 * moduleDescriptorCache.setSettings(ivySettings)
        0 * _._

        assert settings.is(ivySettings)

        UserResolverChain chainResolver = settings.getResolver(DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME)
        assert chainResolver.returnFirst
        assert chainResolver.resolvers.size() == 2

        assert settings.defaultResolver instanceof LoopbackDependencyResolver
        assert settings.defaultResolver.resolver == chainResolver

        [testResolver, testResolver2].each { resolver ->
            assert chainResolver.resolvers.any { it.resolver == resolver }
            assert settings.getResolver(resolver.name).resolver == resolver
            assert resolver.settings == settings
            assert resolver.repositoryCacheManager.settings == settings
        }
    }

    public void shouldReuseResolveSettings() {
        when:
        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], resolutionStrategy)

        then:
        1 * ivySettingsFactory.create() >> ivySettings
        1 * resolutionStrategy.getCachePolicy()
        1 * moduleDescriptorCache.setSettings(ivySettings)
        0 * _._

        assert settings.is(ivySettings)

        UserResolverChain chainResolver = settings.defaultResolver.resolver
        assert chainResolver.resolvers.size() == 2
        [testResolver, testResolver2].each { resolver ->
            assert chainResolver.resolvers.any { it.resolver == resolver }
        }

        when:
        settings = converter.convertForResolve([testResolver], resolutionStrategy)

        then:
        assert settings.is(ivySettings)

        UserResolverChain secondChainResolver = settings.getResolver(DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME)
        assert secondChainResolver.resolvers.size() == 1
        [testResolver].each { resolver ->
            assert secondChainResolver.resolvers.any { it.resolver == resolver }
            assert settings.getResolver(resolver.name).resolver == resolver
            assert resolver.settings == settings
            assert resolver.repositoryCacheManager.settings == settings
        }
    }

    public void testConvertForPublish() {
        when:
        IvySettings settings = converter.convertForPublish([testResolver, testResolver2])

        then:
        settings.is(ivySettings)

        and:
        [testResolver, testResolver2].each {
            it.settings == settings
            it.repositoryCacheManager.settings == settings
        }

        and:
        1 * ivySettingsFactory.create() >> ivySettings
        0 * _._
    }

    public void reusesPublishSettings() {
        when:
        IvySettings settings = converter.convertForPublish([testResolver])

        then:
        settings.is(ivySettings)

        and:
        1 * ivySettingsFactory.create() >> ivySettings
        0 * _._

        when:
        settings = converter.convertForPublish([testResolver, testResolver2])

        then:
        settings.is(ivySettings)

        and:
            [testResolver, testResolver2].each {
            it.settings == settings
            it.repositoryCacheManager.settings == settings
        }
    }
}
