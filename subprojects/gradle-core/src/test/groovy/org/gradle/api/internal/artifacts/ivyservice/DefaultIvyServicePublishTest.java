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
import org.apache.ivy.core.publish.PublishEngine;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyServicePublishTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private ModuleDescriptor moduleDescriptorMock = context.mock(ModuleDescriptor.class);
    private PublishEngine publishEngineDummy = context.mock(PublishEngine.class);
    private InternalRepository internalRepositoryDummy = context.mock(InternalRepository.class);
    private DependencyMetaDataProvider dependencyMetaDataProviderMock = context.mock(DependencyMetaDataProvider.class);

    @Test
    public void testPublish() throws IOException, ParseException {
        final IvySettings ivySettingsDummy = new IvySettings();
        final Set<Configuration> configurations = createConfigurations();
        final File someDescriptorDestination = new File("somePath");
        final List<DependencyResolver> publishResolversDummy = createPublishResolversDummy();
        final Module moduleForResolveDummy = context.mock(Module.class, "moduleForResolve");
        final Module moduleForPublicDescriptorDummy = context.mock(Module.class, "moduleForPublicDescriptor");
        File cacheParentDirDummy = createCacheParentDirDummy();
        final DefaultIvyService ivyService = createIvyService();

        setUpForPublish(configurations, publishResolversDummy, moduleForResolveDummy, moduleForPublicDescriptorDummy, cacheParentDirDummy,
                ivyService, ivySettingsDummy);

        final Set<String> expectedConfigurations = Configurations.getNames(configurations, true);
        context.checking(new Expectations() {{
            one(ivyService.getIvyFileConverter()).convert(configurations, moduleForPublicDescriptorDummy, ivySettingsDummy);
            will(returnValue(moduleDescriptorMock));
            one(moduleDescriptorMock).toIvyFile(someDescriptorDestination);
            one(ivyService.getDependencyPublisher()).publish(expectedConfigurations,
                    publishResolversDummy, moduleDescriptorMock, someDescriptorDestination, publishEngineDummy);
        }});

        ivyService.publish(configurations, someDescriptorDestination, publishResolversDummy);
    }

    private DefaultIvyService createIvyService() {
        SettingsConverter settingsConverterStub = context.mock(SettingsConverter.class);
        ModuleDescriptorConverter resolveModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "resolve");
        ModuleDescriptorConverter publishModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "publishConverter");
        IvyFileConverter ivyFileConverter = context.mock(IvyFileConverter.class);
        IvyDependencyPublisher ivyDependencyPublisherMock = context.mock(IvyDependencyPublisher.class);
        ResolverProvider resolverProvider = context.mock(ResolverProvider.class);

        DefaultIvyService ivyService = new DefaultIvyService(dependencyMetaDataProviderMock, resolverProvider,
                settingsConverterStub, resolveModuleDescriptorConverter, publishModuleDescriptorConverter, ivyFileConverter,
                new DefaultIvyFactory(), context.mock(IvyDependencyResolver.class),
                ivyDependencyPublisherMock, new HashMap());

        return ivyService;
    }

    private File createCacheParentDirDummy() {
        return new File("cacheParentDirDummy");
    }

    private List<DependencyResolver> createPublishResolversDummy() {
        return WrapUtil.toList(context.mock(DependencyResolver.class, "publish"));
    }

    private Set<Configuration> createConfigurations() {
        final Configuration configurationStub1 = context.mock(Configuration.class, "confStub1");
        final Configuration configurationStub2 = context.mock(Configuration.class, "confStub2");
        context.checking(new Expectations() {{
            allowing(configurationStub1).getName();
            will(returnValue("conf1"));

            allowing(configurationStub1).getHierarchy();
            will(returnValue(WrapUtil.toLinkedSet(configurationStub1)));

            allowing(configurationStub2).getName();
            will(returnValue("conf2"));

            allowing(configurationStub2).getHierarchy();
            will(returnValue(WrapUtil.toLinkedSet(configurationStub2)));
        }});
        return WrapUtil.toSet(configurationStub1, configurationStub2);
    }

    private void setUpForPublish(final Set<Configuration> configurations,
                                 final List<DependencyResolver> publishResolversDummy, final Module moduleForResolveDummy, final Module moduleForPublicDescriptorDummy,
                                 final File cacheParentDirDummy, final DefaultIvyService ivyService, final IvySettings ivySettingsDummy) {
        context.checking(new Expectations() {{
            allowing(dependencyMetaDataProviderMock).getInternalRepository();
            will(returnValue(internalRepositoryDummy));

            allowing(dependencyMetaDataProviderMock).getGradleUserHomeDir();
            will(returnValue(cacheParentDirDummy));

            allowing(dependencyMetaDataProviderMock).getModuleForResolve();
            will(returnValue(moduleForResolveDummy));

            allowing(dependencyMetaDataProviderMock).getModuleForPublicDescriptor();
            will(returnValue(moduleForPublicDescriptorDummy));

            allowing(ivyService.getSettingsConverter()).convertForPublish(publishResolversDummy, cacheParentDirDummy,
                    internalRepositoryDummy);
            will(returnValue(ivySettingsDummy));

            allowing(setUpIvyFactory(ivySettingsDummy, ivyService)).getPublishEngine();
            will(returnValue(publishEngineDummy));

            allowing(ivyService.getPublishModuleDescriptorConverter()).convert(configurations,
                    moduleForResolveDummy, ivySettingsDummy);
            will(returnValue(moduleDescriptorMock));
        }});
    }

    private Ivy setUpIvyFactory(final IvySettings ivySettingsDummy, DefaultIvyService ivyService) {
        final IvyFactory ivyFactoryStub = context.mock(IvyFactory.class);
        final Ivy ivyStub = context.mock(Ivy.class);
        context.checking(new Expectations() {{
            allowing(ivyFactoryStub).createIvy(ivySettingsDummy);
            will(returnValue(ivyStub));

            allowing(ivyStub).getSettings();
            will(returnValue(ivySettingsDummy));
        }});
        ivyService.setIvyFactory(ivyFactoryStub);
        return ivyStub;
    }
}