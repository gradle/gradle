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

package org.gradle.api.internal.dependencies.ivyservice

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.ChainResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.DependencyManager
import org.gradle.api.Transformer
import org.gradle.api.internal.dependencies.ivyservice.ClientModuleResolver
import org.gradle.api.internal.dependencies.ivyservice.DefaultSettingsConverter
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import static org.hamcrest.Matchers.sameInstance
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * @author Hans Dockter
 */
class DefaultSettingsConverterTest {
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

    @Test public void testConvert() {
        IvySettings settings = converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry)
        ChainResolver chainResolver = settings.getResolver(DefaultSettingsConverter.CHAIN_RESOLVER_NAME)
        assertEquals(2, chainResolver.resolvers.size())
        assert chainResolver.resolvers[0].name.is(TEST_BUILD_RESOLVER.name)
        assert chainResolver.resolvers[1].is(TEST_RESOLVER)
        assertTrue chainResolver.returnFirst

        ClientModuleResolver clientModuleResolver = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_NAME)
        ChainResolver clientModuleChain = settings.getResolver(DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME)
        assertTrue clientModuleChain.returnFirst
        assert clientModuleChain.resolvers[0].is(clientModuleResolver)
        assert clientModuleChain.resolvers[1].is(chainResolver)
        assert settings.defaultResolver.is(clientModuleChain)

        [TEST_BUILD_RESOLVER.name, TEST_RESOLVER.name, DefaultSettingsConverter.CHAIN_RESOLVER_NAME,
                DefaultSettingsConverter.CLIENT_MODULE_NAME, DefaultSettingsConverter.CLIENT_MODULE_CHAIN_NAME].each {
            assert settings.getResolver(it)
            assert settings.getResolver(it).getRepositoryCacheManager().settings == settings
        }

        assert settings.getResolver(TEST_UPLOAD_RESOLVER.name).is(TEST_UPLOAD_RESOLVER)
        assertEquals(new File(testGradleUserHome, DependencyManager.DEFAULT_CACHE_DIR_NAME),
                settings.defaultCache)
        assertEquals(settings.defaultCacheArtifactPattern, DependencyManager.DEFAULT_CACHE_ARTIFACT_PATTERN)
    }

    @Test public void testWithGivenSettings() {
        IvySettings ivySettings = [:] as IvySettings
        converter.ivySettings = ivySettings
        assert ivySettings.is(converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], new File(''),
                TEST_BUILD_RESOLVER, clientModuleRegistry))
    }

    @Test
    public void testTransformerCanModifyIvyDescriptor() {
        final IvySettings original = context.mock(IvySettings.class, "original");
        final IvySettings transformed = context.mock(IvySettings.class, "transformed");
        final Transformer<IvySettings> transformer = context.mock(Transformer.class);
        context.checking {
            one(transformer).transform(with(sameInstance(original)));
            will(returnValue(transformed));
        };

        converter.addIvyTransformer(transformer);

        IvySettings ivySettings = converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry)
        assertThat(ivySettings, sameInstance(ivySettings));
    }

    @Test
    public void testTransformationClosureCanModifyIvyDescriptor() {
        final DependencyDescriptor transformed = context.mock(DependencyDescriptor.class, "transformed");

        converter.addIvyTransformer(HelperUtil.returns(transformed));

        IvySettings ivySettings = converter.convert([TEST_RESOLVER], [TEST_UPLOAD_RESOLVER], testGradleUserHome,
                TEST_BUILD_RESOLVER, clientModuleRegistry)
        assertThat(ivySettings, sameInstance(ivySettings));
    }
}
