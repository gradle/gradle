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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.internal.dependencies.SettingsConverter
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.DependencyManager

/**
 * @author Hans Dockter
 */
class SettingsConverterTest extends GroovyTestCase {
    static final IBiblioResolver TEST_RESOLVER = new IBiblioResolver()
    static {
        TEST_RESOLVER.name = 'resolver'
    }

    static final IBiblioResolver TEST_UPLOAD_RESOLVER = new IBiblioResolver()
    static {
        TEST_UPLOAD_RESOLVER.name = 'uploadResolver'
    }

    static final IBiblioResolver TEST_BUILD_RESOLVER = new IBiblioResolver()
    static {
        TEST_BUILD_RESOLVER.name = 'buildResolver'
    }

    SettingsConverter converter

    Map clientModuleRegistry

    File testGradleUserHome

    void setUp() {
        converter = new SettingsConverter()
        clientModuleRegistry = [a: 'b']
        testGradleUserHome = new File('gradleUserHome')
    }

    void testConvert() {
        IvySettings settings = converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry, null)
        ChainResolver chainResolver = settings.getResolver(SettingsConverter.CHAIN_RESOLVER_NAME)
        assertEquals(2, chainResolver.resolvers.size())
        assert chainResolver.resolvers[0].name.is(TEST_BUILD_RESOLVER.name)
        assert chainResolver.resolvers[1].is(TEST_RESOLVER)
        assertTrue chainResolver.returnFirst

        ClientModuleResolver clientModuleResolver = settings.getResolver(SettingsConverter.CLIENT_MODULE_NAME)
        ChainResolver clientModuleChain = settings.getResolver(SettingsConverter.CLIENT_MODULE_CHAIN_NAME)
        assertTrue clientModuleChain.returnFirst
        assert clientModuleChain.resolvers[0].is(clientModuleResolver)
        assert clientModuleChain.resolvers[1].is(chainResolver)
        assert settings.defaultResolver.is(clientModuleChain)

        [TEST_BUILD_RESOLVER.name, TEST_RESOLVER.name, SettingsConverter.CHAIN_RESOLVER_NAME,
                SettingsConverter.CLIENT_MODULE_NAME, SettingsConverter.CLIENT_MODULE_CHAIN_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager().settings == settings
        }

        assert settings.getResolver(TEST_UPLOAD_RESOLVER.name).is(TEST_UPLOAD_RESOLVER)
        assertEquals(new File(testGradleUserHome, DependencyManager.DEFAULT_CACHE_DIR_NAME),
                settings.defaultCache)
        assertEquals(settings.defaultCacheArtifactPattern, DependencyManager.DEFAULT_CACHE_ARTIFACT_PATTERN)
    }

    void testConvertWithClientChainConfigurer() {
        IvySettings settings = converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry) {
            returnFirst = false
        }
        assertFalse settings.getResolver(SettingsConverter.CHAIN_RESOLVER_NAME).returnFirst
    }

    void testWithGivenSettings() {
        IvySettings ivySettings = [:] as IvySettings
        converter.ivySettings = ivySettings
        assert ivySettings.is(converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], new File(''),
                TEST_BUILD_RESOLVER, clientModuleRegistry, null))
    }
}
