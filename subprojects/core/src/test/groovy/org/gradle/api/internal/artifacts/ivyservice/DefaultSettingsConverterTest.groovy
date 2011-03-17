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
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertSame

/**
 * @author Hans Dockter
 */
class DefaultSettingsConverterTest {
    static final IBiblioResolver TEST_RESOLVER = new IBiblioResolver()
    static final IBiblioResolver TEST_RESOLVER_2 = new IBiblioResolver()
    static {
        TEST_RESOLVER.name = 'resolver'
    }

    static final IBiblioResolver TEST_BUILD_RESOLVER = new IBiblioResolver()
    static {
        TEST_BUILD_RESOLVER.name = 'buildResolver'
    }

    DefaultSettingsConverter converter

    Map clientModuleRegistry

    File testGradleUserHome

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp()  {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        converter = new DefaultSettingsConverter()
        clientModuleRegistry = [a: [:] as ModuleDescriptor]
        testGradleUserHome = new File('gradleUserHome')
    }

    @Test public void testConvertForResolve() {
        IvySettings settings = converter.convertForResolve([TEST_RESOLVER, TEST_RESOLVER_2], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry)
        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assertEquals(3, chainResolver.resolvers.size())
        assert chainResolver.resolvers[0].name.is(TEST_BUILD_RESOLVER.name)
        assert chainResolver.resolvers[1].is(TEST_RESOLVER)
        assert chainResolver.resolvers[2].is(TEST_RESOLVER_2)
        assertTrue chainResolver.returnFirst

        ClientModuleResolver clientModuleResolver = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)
        ChainResolver clientModuleChain = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME)
        assertTrue clientModuleChain.returnFirst
        assert clientModuleChain.resolvers[0].is(clientModuleResolver)
        assert clientModuleChain.resolvers[1].is(chainResolver)
        assert settings.defaultResolver.is(clientModuleChain)

        [TEST_BUILD_RESOLVER.name, TEST_RESOLVER.name, TEST_RESOLVER_2.name, DefaultSettingsConverter.CHAIN_RESOLVER_NAME,
                DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager().settings == settings
        }
        assert settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)

        assertEquals(new File(testGradleUserHome, ResolverContainer.DEFAULT_CACHE_DIR_NAME),
                settings.defaultCache)
        assertEquals(settings.defaultCacheArtifactPattern, ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN)
    }

    @Test public void testConvertForPublish() {
        IvySettings settings = converter.convertForPublish([TEST_RESOLVER, TEST_RESOLVER_2], testGradleUserHome,
                TEST_BUILD_RESOLVER)
        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assertEquals(1, chainResolver.resolvers.size())
        assert chainResolver.resolvers[0].name.is(TEST_BUILD_RESOLVER.name)
        assertTrue chainResolver.returnFirst

        ClientModuleResolver clientModuleResolver = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)
        ChainResolver clientModuleChain = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME)
        assertTrue clientModuleChain.returnFirst
        assert clientModuleChain.resolvers[0].is(clientModuleResolver)
        assert clientModuleChain.resolvers[1].is(chainResolver)
        assert settings.defaultResolver.is(clientModuleChain)

        [TEST_BUILD_RESOLVER.name, TEST_RESOLVER.name, TEST_RESOLVER_2.name, DefaultSettingsConverter.CHAIN_RESOLVER_NAME,
                DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager().settings == settings
        }
        assert settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)

        assert settings.getResolver(TEST_RESOLVER.name).is(TEST_RESOLVER)
        assert settings.getResolver(TEST_RESOLVER_2.name).is(TEST_RESOLVER_2)
        assertEquals(new File(testGradleUserHome, ResolverContainer.DEFAULT_CACHE_DIR_NAME),
                settings.defaultCache)
        assertEquals(settings.defaultCacheArtifactPattern, ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN)
    }

    @Test
    public void repositoryCacheManagerShouldBeSharedBetweenSettings() {
        IvySettings settings1 = converter.convertForPublish([TEST_RESOLVER, TEST_RESOLVER_2], testGradleUserHome,
                TEST_BUILD_RESOLVER)
        IvySettings settings2 = converter.convertForPublish([TEST_RESOLVER, TEST_RESOLVER_2], testGradleUserHome,
                TEST_BUILD_RESOLVER)
        IvySettings settings3 = converter.convertForResolve([TEST_RESOLVER, TEST_RESOLVER_2], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry)
        assertSame(settings1.getDefaultRepositoryCacheManager(), settings2.getDefaultRepositoryCacheManager())
        assertSame(settings1.getDefaultRepositoryCacheManager(), settings3.getDefaultRepositoryCacheManager())

    }

    @Test public void testWithGivenSettings() {
        IvySettings ivySettings = [:] as IvySettings
        converter.ivySettings = ivySettings
        assert ivySettings.is(converter.convertForResolve([TEST_RESOLVER], new File(''),
                TEST_BUILD_RESOLVER, clientModuleRegistry))
        assert ivySettings.is(converter.convertForPublish([TEST_RESOLVER], new File(''),
                TEST_BUILD_RESOLVER))
    }
}
