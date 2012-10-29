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
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.*;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class IvyBackedArtifactPublisherTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();

    private ModuleDescriptor publishModuleDescriptorDummy = context.mock(ModuleDescriptor.class);
    private ModuleDescriptor fileModuleDescriptorMock = context.mock(ModuleDescriptor.class);
    private DependencyMetaDataProvider dependencyMetaDataProviderMock = context.mock(DependencyMetaDataProvider.class);
    private ResolverProvider resolverProvider = context.mock(ResolverProvider.class);
    private IvyFactory ivyFactoryStub = context.mock(IvyFactory.class);
    private SettingsConverter settingsConverterStub = context.mock(SettingsConverter.class);
    private IvyDependencyPublisher ivyDependencyPublisherMock = context.mock(IvyDependencyPublisher.class);
    private ModuleDescriptorConverter publishModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "publishConverter");
    private ModuleDescriptorConverter fileModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "fileConverter");
    private IvyModuleDescriptorWriter ivyModuleDescriptorWriterMock = context.mock(IvyModuleDescriptorWriter.class);

    @Test
    public void testPublish() throws IOException, ParseException {
        final IvySettings ivySettingsDummy = new IvySettings();
        final EventManager ivyEventManagerDummy = new EventManager();
        final ConfigurationInternal configuration = context.mock(ConfigurationInternal.class);
        final Set<Configuration> configurations = createConfiguration();
        final File someDescriptorDestination = new File("somePath");
        final List<DependencyResolver> publishResolversDummy = createPublishResolversDummy();
        final Module moduleDummy = context.mock(Module.class, "moduleForResolve");
        final IvyBackedArtifactPublisher ivyService = createIvyService();

        setUpIvyFactory(ivySettingsDummy, ivyEventManagerDummy);
        setUpForPublish(configurations, publishResolversDummy, moduleDummy, ivySettingsDummy);

        final Set<String> expectedConfigurations = Configurations.getNames(configurations, true);
        context.checking(new Expectations() {{
            allowing(configuration).getHierarchy();
            will(returnValue(configurations));
            allowing(configuration).getModule();
            will(returnValue(moduleDummy));
            allowing(resolverProvider).getResolvers();
            will(returnValue(publishResolversDummy));
            allowing(configuration).getResolutionStrategy();
            will(returnValue(new DefaultResolutionStrategy()));
            one(ivyDependencyPublisherMock).publish(expectedConfigurations,
                    publishResolversDummy, publishModuleDescriptorDummy, someDescriptorDestination, ivyEventManagerDummy);
            allowing(ivyModuleDescriptorWriterMock).write(fileModuleDescriptorMock, someDescriptorDestination, null);
        }});

        ivyService.publish(configuration.getModule(), configuration, someDescriptorDestination, null);
    }

    private IvyBackedArtifactPublisher createIvyService() {
        return new IvyBackedArtifactPublisher(resolverProvider,
                settingsConverterStub,
                publishModuleDescriptorConverter,
                fileModuleDescriptorConverter,
                ivyFactoryStub,
                ivyDependencyPublisherMock,
                ivyModuleDescriptorWriterMock);
    }

    private List<DependencyResolver> createPublishResolversDummy() {
        return WrapUtil.toList(context.mock(DependencyResolver.class, "publish"));
    }

    private Set<Configuration> createConfiguration() {
        final Configuration configurationStub1 = context.mock(Configuration.class, "confStub1");
        final Configuration configurationStub2 = context.mock(Configuration.class, "confStub2");
        context.checking(new Expectations() {{
            allowing(configurationStub1).getName();
            will(returnValue("conf1"));

            allowing(configurationStub1).getHierarchy();
            will(returnValue(WrapUtil.toLinkedSet(configurationStub1)));

            allowing(configurationStub1).getAll();
            will(returnValue(WrapUtil.toLinkedSet(configurationStub1, configurationStub2)));

            allowing(configurationStub2).getName();
            will(returnValue("conf2"));

            allowing(configurationStub2).getHierarchy();
            will(returnValue(WrapUtil.toLinkedSet(configurationStub2)));

            allowing(configurationStub2).getAll();
            will(returnValue(WrapUtil.toLinkedSet(configurationStub1, configurationStub2)));
        }});
        return WrapUtil.toSet(configurationStub1, configurationStub2);
    }

    private void setUpForPublish(final Set<Configuration> configurations,
                                 final List<DependencyResolver> publishResolversDummy, final Module moduleDummy,
                                 final IvySettings ivySettingsDummy) {
        context.checking(new Expectations() {{
            allowing(dependencyMetaDataProviderMock).getModule();
            will(returnValue(moduleDummy));

            allowing(settingsConverterStub).convertForPublish(publishResolversDummy);
            will(returnValue(ivySettingsDummy));

            allowing(publishModuleDescriptorConverter).convert(with(equalTo(configurations)),
                    with(equalTo(moduleDummy)));
            will(returnValue(publishModuleDescriptorDummy));

            allowing(fileModuleDescriptorConverter).convert(with(equalTo(configurations)),
                    with(equalTo(moduleDummy)));
            will(returnValue(fileModuleDescriptorMock));

        }});
    }

    private Ivy setUpIvyFactory(final IvySettings ivySettingsDummy, final EventManager ivyEventManagerDummy) {
        final Ivy ivyStub = context.mock(Ivy.class);
        context.checking(new Expectations() {{
            allowing(ivyFactoryStub).createIvy(ivySettingsDummy);
            will(returnValue(ivyStub));

            allowing(ivyStub).getSettings();
            will(returnValue(ivySettingsDummy));

            allowing(ivyStub).getEventManager();
            will(returnValue(ivyEventManagerDummy));
        }});
        return ivyStub;
    }
}
