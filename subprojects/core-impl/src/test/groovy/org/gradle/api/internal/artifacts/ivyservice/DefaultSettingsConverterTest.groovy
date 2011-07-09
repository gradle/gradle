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
    final IBiblioResolver testResolver = new IBiblioResolver()
    final IBiblioResolver testResolver2 = new IBiblioResolver()
    final IBiblioResolver testBuildResolver = new IBiblioResolver()

    DefaultSettingsConverter converter

    Map clientModuleRegistry

    File testGradleUserHome

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp()  {
        testResolver.name = 'resolver'
        testBuildResolver.name = 'buildResolver'
        context.setImposteriser(ClassImposteriser.INSTANCE)
        converter = new DefaultSettingsConverter()
        clientModuleRegistry = [a: [:] as ModuleDescriptor]
        testGradleUserHome = new File('gradleUserHome')
    }

    @Test public void testConvertForResolve() {
        IvySettings settings = converter.convertForResolve([testResolver, testResolver2], testGradleUserHome,
                testBuildResolver, clientModuleRegistry)
        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assertEquals(3, chainResolver.resolvers.size())
        assert chainResolver.resolvers[0].name.is(testBuildResolver.name)
        assert chainResolver.resolvers[1].is(testResolver)
        assert chainResolver.resolvers[2].is(testResolver2)
        assertTrue chainResolver.returnFirst

        ClientModuleResolver clientModuleResolver = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)
        ChainResolver clientModuleChain = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME)
        assertTrue clientModuleChain.returnFirst
        assert clientModuleChain.resolvers[0].is(clientModuleResolver)
        assert clientModuleChain.resolvers[1].is(chainResolver)
        assert settings.defaultResolver.is(clientModuleChain)

        [testBuildResolver.name, testResolver.name, testResolver2.name].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager().settings == settings
        }
        [DefaultSettingsConverter.CLIENT_MODULE_NAME, DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME, DefaultSettingsConverter.CHAIN_RESOLVER_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager() instanceof NoOpRepositoryCacheManager
        }

        assertEquals(new File(testGradleUserHome, ResolverContainer.DEFAULT_CACHE_DIR_NAME),
                settings.defaultCache)
        assertEquals(settings.defaultCacheArtifactPattern, ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN)
    }

    @Test public void testConvertForPublish() {
        IvySettings settings = converter.convertForPublish([testResolver, testResolver2], testGradleUserHome,
                testBuildResolver)

        [testResolver.name, testResolver2.name].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager().settings == settings
        }

        assert settings.getResolver(testResolver.name).is(testResolver)
        assert settings.getResolver(testResolver2.name).is(testResolver2)
        assertEquals(new File(testGradleUserHome, ResolverContainer.DEFAULT_CACHE_DIR_NAME),
                settings.defaultCache)
        assertEquals(settings.defaultCacheArtifactPattern, ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN)
    }

    @Test
    public void repositoryCacheManagerShouldBeSharedBetweenSettings() {
        IvySettings settings1 = converter.convertForPublish([testResolver, testResolver2], testGradleUserHome,
                testBuildResolver)
        IvySettings settings2 = converter.convertForPublish([testResolver, testResolver2], testGradleUserHome,
                testBuildResolver)
        IvySettings settings3 = converter.convertForResolve([testResolver, testResolver2], testGradleUserHome,
                testBuildResolver, clientModuleRegistry)
        assertSame(settings1.getDefaultRepositoryCacheManager(), settings2.getDefaultRepositoryCacheManager())
        assertSame(settings1.getDefaultRepositoryCacheManager(), settings3.getDefaultRepositoryCacheManager())

    }

    @Test public void testWithGivenSettings() {
        IvySettings ivySettings = [:] as IvySettings
        converter.ivySettings = ivySettings
        assert ivySettings.is(converter.convertForResolve([testResolver], new File(''),
                testBuildResolver, clientModuleRegistry))
        assert ivySettings.is(converter.convertForPublish([testResolver], new File(''),
                testBuildResolver))
    }
}
