/*
 * Copyright 2007-2009 the original author or authors.
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
import org.gradle.api.artifacts.PublishInstruction;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyService_PublishTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private ModuleDescriptor moduleDescriptorDummy = HelperUtil.createModuleDescriptor(WrapUtil.toSet("someConf"));
    private PublishEngine publishEngineDummy = context.mock(PublishEngine.class);


    @Test
    public void testPublish() {
        final Set<Configuration> configurations = createConfigurations();
        final PublishInstruction publishInstructionDummy = new PublishInstruction();
        final List<DependencyResolver> publishResolversDummy = createPublishResolversDummy();
        Module moduleDummy = context.mock(Module.class);
        File cacheParentDirDummy = createCacheParentDirDummy();
        final DefaultIvyService ivyService = createIvyService();

        setUpForPublish(configurations, publishInstructionDummy, publishResolversDummy, moduleDummy, cacheParentDirDummy, ivyService);

        final Set<String> expectedConfigurations = Configurations.getNames(configurations, true);
        context.checking(new Expectations() {{
            one(ivyService.getDependencyPublisher()).publish(expectedConfigurations,
                    publishInstructionDummy, publishResolversDummy, moduleDescriptorDummy, publishEngineDummy);
        }});

        ivyService.publish(configurations, publishInstructionDummy, publishResolversDummy,
                moduleDummy, cacheParentDirDummy);
    }

    private DefaultIvyService createIvyService() {
        SettingsConverter settingsConverterStub = context.mock(SettingsConverter.class);
        ModuleDescriptorConverter moduleDescriptorConverterStub = context.mock(ModuleDescriptorConverter.class);
        InternalRepository internalRepositoryDummy = context.mock(InternalRepository.class);
        IvyDependencyPublisher ivyDependencyPublisherMock = context.mock(IvyDependencyPublisher.class);

        DefaultIvyService ivyService = new DefaultIvyService(internalRepositoryDummy);
        ivyService.setModuleDescriptorConverter(moduleDescriptorConverterStub);
        ivyService.setSettingsConverter(settingsConverterStub);
        ivyService.setDependencyPublisher(ivyDependencyPublisherMock);

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
            will(returnValue(Arrays.asList(configurationStub1)));

            allowing(configurationStub2).getName();
            will(returnValue("conf2"));

            allowing(configurationStub2).getHierarchy();
            will(returnValue(Arrays.asList(configurationStub2)));
        }});
        return WrapUtil.toSet(configurationStub1, configurationStub2);
    }

    private void setUpForPublish(final Set<Configuration> configurations, final PublishInstruction publishInstruction,
                                 final List<DependencyResolver> publishResolversDummy, final Module moduleDummy,
                                 final File cacheParentDirDummy, final DefaultIvyService ivyService) {
        final IvySettings ivySettingsDummy = new IvySettings();
        context.checking(new Expectations() {{
            allowing(ivyService.getSettingsConverter()).convertForPublish(publishResolversDummy, cacheParentDirDummy,
                    ivyService.getInternalRepository());
            will(returnValue(ivySettingsDummy));

            allowing(setUpIvyFactory(ivySettingsDummy, ivyService)).getPublishEngine();
            will(returnValue(publishEngineDummy));

            allowing(ivyService.getModuleDescriptorConverter()).convertForPublish(configurations,
                    publishInstruction.isUploadModuleDescriptor(), moduleDummy, ivySettingsDummy);
            will(returnValue(moduleDescriptorDummy));
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