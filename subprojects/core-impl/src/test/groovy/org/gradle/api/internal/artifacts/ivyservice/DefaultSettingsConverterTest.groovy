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
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DefaultSettingsConverterTest extends Specification {
    final IBiblioResolver testResolver = new IBiblioResolver()
    final IBiblioResolver testResolver2 = new IBiblioResolver()

    ResolutionStrategyInternal resolutionStrategy = Mock()
    ModuleResolutionCache dynamicRevisionCache = Mock()
    ModuleDescriptorCache moduleDescriptorCache = Mock()
    FileStore fileStore = Mock()

    File testGradleUserHome = new File('gradleUserHome')

    final Factory<IvySettings> ivySettingsFactory = Mock()
    final IvySettings ivySettings = new IvySettings()

    DefaultSettingsConverter converter = new DefaultSettingsConverter(Mock(ProgressLoggerFactory), ivySettingsFactory, dynamicRevisionCache, moduleDescriptorCache)

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
        assert chainResolver.resolvers == [testResolver, testResolver2]
        assert chainResolver.returnFirst
        assert settings.defaultResolver.is(chainResolver)

        [testResolver.name, testResolver2.name].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).repositoryCacheManager.settings == settings
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

        UserResolverChain chainResolver = settings.defaultResolver
        assert chainResolver.resolvers == [testResolver, testResolver2]

        when:
        settings = converter.convertForResolve([testResolver], resolutionStrategy)

        then:
        assert settings.is(ivySettings)

        UserResolverChain secondChainResolver = settings.getResolver(DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME)
        assert secondChainResolver.resolvers == [testResolver]
        assert !ivySettings.resolvers.contains(testResolver2)
    }

    public void testConvertForPublish() {
        when:
        IvySettings settings = converter.convertForPublish([testResolver, testResolver2])

        then:
        1 * ivySettingsFactory.create() >> ivySettings
        0 * _._

        settings.is(ivySettings)

        settings.resolvers as Set == [testResolver, testResolver2] as Set
        settings.resolvers.each {
            settings.resolvers.contains(it)
            settings.getResolver(it.name).is(it)
            settings.getResolver(it.name).repositoryCacheManager.settings == settings
        }
    }

    public void reusesPublishSettings() {
        when:
        IvySettings settings = converter.convertForPublish([testResolver])

        then:
        1 * ivySettingsFactory.create() >> ivySettings
        0 * _._

        settings.is(ivySettings)
        
        settings.resolvers as Set == [testResolver] as Set

        when:
        settings = converter.convertForPublish([testResolver, testResolver2])

        then:
        settings.is(ivySettings)

        settings.resolvers as Set == [testResolver, testResolver2] as Set
        settings.resolvers.each {
            settings.resolvers.contains(it)
            settings.getResolver(it.name).is(it)
            settings.getResolver(it.name).repositoryCacheManager.settings == settings
        }
    }
}
