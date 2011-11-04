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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.internal.Factory
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DefaultSettingsConverterTest extends Specification {
    final IBiblioResolver testResolver = new IBiblioResolver()
    final IBiblioResolver testResolver2 = new IBiblioResolver()

    Map clientModuleRegistry = [a: [:] as ModuleDescriptor]
    DependencyResolver projectResolver = Mock()
    ResolutionStrategyInternal resolutionStrategy = Mock()
    ModuleResolutionCache dynamicRevisionCache = Mock()

    File testGradleUserHome = new File('gradleUserHome')

    final Factory<IvySettings> ivySettingsFactory = Mock()
    final IvySettings ivySettings = new IvySettings()

    DefaultSettingsConverter converter = new DefaultSettingsConverter(Mock(ProgressLoggerFactory), ivySettingsFactory, dynamicRevisionCache)

    public void setup() {
        testResolver.name = 'resolver'
    }

    public void testConvertForResolve() {
        when:
        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], projectResolver, clientModuleRegistry, resolutionStrategy)

        then:
        1 * ivySettingsFactory.create() >> ivySettings
        1 * resolutionStrategy.getForcedModules()
        1 * resolutionStrategy.getCachePolicy()
        0 * _._

        assert settings.is(ivySettings)

        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME)
        assert chainResolver.resolvers == [testResolver, testResolver2]
        assert chainResolver.returnFirst

        assert settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_RESOLVER_NAME) instanceof ClientModuleResolver
        assert settings.getResolver(DefaultSettingsConverter.TOP_LEVEL_RESOLVER_CHAIN_NAME) instanceof TopLeveResolverChain

        EntryPointResolver entryPointResolver = settings.getResolver(DefaultSettingsConverter.ENTRY_POINT_RESOLVER)
        assert settings.defaultResolver.is(entryPointResolver)

        [testResolver.name, testResolver2.name].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).repositoryCacheManager.settings == settings
        }
        [DefaultSettingsConverter.ENTRY_POINT_RESOLVER, DefaultSettingsConverter.CLIENT_MODULE_RESOLVER_NAME, DefaultSettingsConverter.TOP_LEVEL_RESOLVER_CHAIN_NAME, DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).repositoryCacheManager instanceof NoOpRepositoryCacheManager
        }
    }

    public void shouldReuseResolveSettings() {
        when:
        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], projectResolver, clientModuleRegistry, resolutionStrategy)

        then:
        1 * ivySettingsFactory.create() >> ivySettings
        1 * resolutionStrategy.getForcedModules()
        1 * resolutionStrategy.getCachePolicy()
        0 * _._

        assert settings.is(ivySettings)

        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME)
        assert chainResolver.resolvers == [testResolver, testResolver2]

        when:
        settings = converter.convertForResolve([testResolver], projectResolver, clientModuleRegistry, resolutionStrategy)

        then:
        assert settings.is(ivySettings)

        ChainResolver secondChainResolver = settings.getResolver(DefaultSettingsConverter.USER_RESOLVER_CHAIN_NAME)
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
