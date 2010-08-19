/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyServiceResolveTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private Module moduleDummy = context.mock(Module.class);
    private File cacheParentDirDummy = new File("cacheParentDirDummy");
    private Map<String, ModuleDescriptor> clientModuleRegistryDummy = WrapUtil.toMap("a", context.mock(ModuleDescriptor.class));
    private List<DependencyResolver> dependencyResolversDummy = WrapUtil.toList(context.mock(DependencyResolver.class, "dependencies"));

    private InternalRepository internalRepositoryDummy = context.mock(InternalRepository.class);
    private DependencyMetaDataProvider dependencyMetaDataProviderMock = context.mock(DependencyMetaDataProvider.class);
    private ResolverProvider resolverProvider = context.mock(ResolverProvider.class);

    // SUT
    private DefaultIvyService ivyService;

    @Before
    public void setUp() {
        SettingsConverter settingsConverterMock = context.mock(SettingsConverter.class);
        ModuleDescriptorConverter resolveModuleDescriptorConverterStub = context.mock(ModuleDescriptorConverter.class, "resolve");
        ModuleDescriptorConverter publishModuleDescriptorConverterDummy = context.mock(ModuleDescriptorConverter.class, "publish");
        IvyDependencyResolver ivyDependencyResolverMock = context.mock(IvyDependencyResolver.class);

        context.checking(new Expectations() {{
            allowing(dependencyMetaDataProviderMock).getInternalRepository();
            will(returnValue(internalRepositoryDummy));

            allowing(dependencyMetaDataProviderMock).getGradleUserHomeDir();
            will(returnValue(cacheParentDirDummy));

            allowing(dependencyMetaDataProviderMock).getModule();
            will(returnValue(moduleDummy));

            allowing(resolverProvider).getResolvers();
            will(returnValue(dependencyResolversDummy));
        }});

        ivyService = new DefaultIvyService(dependencyMetaDataProviderMock, resolverProvider,
                settingsConverterMock, resolveModuleDescriptorConverterStub, publishModuleDescriptorConverterDummy,
                publishModuleDescriptorConverterDummy,
                new DefaultIvyFactory(), ivyDependencyResolverMock, 
                context.mock(IvyDependencyPublisher.class), clientModuleRegistryDummy);
    }

    @Test
    public void testResolve() {
        final Configuration configurationDummy = context.mock(Configuration.class);
        final Set<Configuration> configurations = WrapUtil.toSet(configurationDummy);
        final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
        final ModuleDescriptor moduleDescriptorDummy = HelperUtil.createModuleDescriptor(WrapUtil.toSet("someConf"));
        final IvyFactory ivyFactoryStub = context.mock(IvyFactory.class);
        final Ivy ivyStub = context.mock(Ivy.class);
        final IvySettings ivySettingsDummy = new IvySettings();

        context.checking(new Expectations() {{
            allowing(ivyFactoryStub).createIvy(ivySettingsDummy);
            will(returnValue(ivyStub));

            allowing(configurationDummy).getAll();
            will(returnValue(configurations));

            allowing(ivyStub).getSettings();
            will(returnValue(ivySettingsDummy));

            allowing(ivyService.getDependencyResolver()).resolve(configurationDummy, ivyStub, moduleDescriptorDummy);
            will(returnValue(resolvedConfiguration));

            allowing(ivyService.getResolveModuleDescriptorConverter()).convert(WrapUtil.toSet(configurationDummy), moduleDummy,
                    ivySettingsDummy);
            will(returnValue(moduleDescriptorDummy));

            allowing(ivyService.getSettingsConverter()).convertForResolve(dependencyResolversDummy, cacheParentDirDummy,
                    internalRepositoryDummy, clientModuleRegistryDummy);
            will(returnValue(ivySettingsDummy));
        }});

        ivyService.setIvyFactory(ivyFactoryStub);
        assertThat(ivyService.resolve(configurationDummy), sameInstance(resolvedConfiguration));
    }
}