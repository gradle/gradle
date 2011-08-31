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
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.internal.Factory
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith(JMock)
class DefaultSettingsConverterTest {
    final IBiblioResolver testResolver = new IBiblioResolver()
    final IBiblioResolver testResolver2 = new IBiblioResolver()

    DefaultSettingsConverter converter

    Map clientModuleRegistry

    File testGradleUserHome

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    final Factory<IvySettings> ivySettingsFactory = context.mock(Factory)
    final IvySettings ivySettings = new IvySettings()

    @Before public void setUp() {
        testResolver.name = 'resolver'
        clientModuleRegistry = [a: [:] as ModuleDescriptor]
        converter = new DefaultSettingsConverter(context.mock(ProgressLoggerFactory), ivySettingsFactory)
        testGradleUserHome = new File('gradleUserHome')
    }

    @Test public void testConvertForResolve() {
        context.checking {
            one(ivySettingsFactory).create()
            will(returnValue(ivySettings))
        }

        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], clientModuleRegistry)
        assert settings.is(ivySettings)

        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assert chainResolver.resolvers == [testResolver, testResolver2]
        assert chainResolver.returnFirst

        ClientModuleResolver clientModuleResolver = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)
        ChainResolver clientModuleChain = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME)
        assert clientModuleChain.resolvers == [clientModuleResolver, chainResolver]
        assert clientModuleChain.returnFirst
        assert settings.defaultResolver.is(clientModuleChain)

        [testResolver.name, testResolver2.name].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).repositoryCacheManager.settings == settings
        }
        [DefaultSettingsConverter.CLIENT_MODULE_NAME, DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME, DefaultSettingsConverter.CHAIN_RESOLVER_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).repositoryCacheManager instanceof NoOpRepositoryCacheManager
        }
    }

    @Test public void testConvertForPublish() {
        context.checking {
            one(ivySettingsFactory).create()
            will(returnValue(ivySettings))
        }

        IvySettings settings = converter.convertForPublish([testResolver, testResolver2])
        assert settings.is(ivySettings)

        assert settings.resolvers as Set == [testResolver, testResolver2] as Set
        settings.resolvers.each {
            assert settings.resolvers.contains(it)
            assert settings.getResolver(it.name).is(it)
            assert settings.getResolver(it.name).repositoryCacheManager.settings == settings
        }
    }

    @Test
    public void shouldReuseResolveSettings() {
        context.checking {
            one(ivySettingsFactory).create()
            will(returnValue(ivySettings))
        }

        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], clientModuleRegistry)
        assert settings.is(ivySettings)

        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assert chainResolver.resolvers == [testResolver, testResolver2]

        settings = converter.convertForResolve([testResolver], clientModuleRegistry)
        assert settings.is(ivySettings)

        chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assert chainResolver.resolvers == [testResolver]
        assert !ivySettings.resolvers.contains(testResolver2)
    }

    @Test
    public void reusesPublishSettings() {
        context.checking {
            one(ivySettingsFactory).create()
            will(returnValue(ivySettings))
        }

        IvySettings settings = converter.convertForPublish([testResolver])
        assert settings.is(ivySettings)
        assert settings.resolvers as Set == [testResolver] as Set

        settings = converter.convertForPublish([testResolver, testResolver2])
        assert settings.is(ivySettings)

        assert settings.resolvers as Set == [testResolver, testResolver2] as Set
        settings.resolvers.each {
            assert settings.resolvers.contains(it)
            assert settings.getResolver(it.name).is(it)
            assert settings.getResolver(it.name).repositoryCacheManager.settings == settings
        }
    }
}
