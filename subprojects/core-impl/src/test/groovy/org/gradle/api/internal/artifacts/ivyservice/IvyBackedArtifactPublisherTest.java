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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
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
    private IvyFactory ivyFactoryStub = context.mock(IvyFactory.class);
    private SettingsConverter settingsConverterStub = context.mock(SettingsConverter.class);
    private IvyDependencyPublisher ivyDependencyPublisherMock = context.mock(IvyDependencyPublisher.class);
    private ModuleDescriptorConverter publishModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "publishConverter");
    private ModuleVersionPublisher publisher1 = context.mock(ModuleVersionPublisher.class);
    private ModuleVersionPublisher publisher2 = context.mock(ModuleVersionPublisher.class);
    private PublicationAwareRepository repo1 = context.mock(PublicationAwareRepository.class);
    private PublicationAwareRepository repo2 = context.mock(PublicationAwareRepository.class);
    final List<ModuleVersionPublisher> publishResolversDummy = WrapUtil.toList(publisher1, publisher2);
    final List<PublicationAwareRepository> publishRepositoriesDummy = WrapUtil.toList(repo1, repo2);

    @Test
    public void testPublish() throws IOException, ParseException {
        final IvySettings ivySettingsDummy = new IvySettings();
        final Set<Configuration> configurations = createConfiguration();
        final File someDescriptorDestination = new File("somePath");
        final Module moduleDummy = context.mock(Module.class, "moduleForResolve");
        final Ivy ivyStub = context.mock(Ivy.class);
        final IvyBackedArtifactPublisher ivyService = createIvyService();

        final Set<String> expectedConfigurations = Configurations.getNames(configurations, true);
        context.checking(new Expectations() {{
            one(ivyFactoryStub).createIvy(ivySettingsDummy);
            will(returnValue(ivyStub));

            allowing(ivyStub).getSettings();
            will(returnValue(ivySettingsDummy));

            one(settingsConverterStub).convertForPublish();
            will(returnValue(ivySettingsDummy));

            one(publishModuleDescriptorConverter).convert(with(equalTo(configurations)),
                    with(equalTo(moduleDummy)));
            will(returnValue(publishModuleDescriptorDummy));

            one(repo1).createPublisher();
            will(returnValue(publisher1));

            one(repo2).createPublisher();
            will(returnValue(publisher2));

            one(publisher1).setSettings(ivySettingsDummy);
            one(publisher2).setSettings(ivySettingsDummy);

            one(ivyDependencyPublisherMock).publish(expectedConfigurations, publishResolversDummy, publishModuleDescriptorDummy, someDescriptorDestination);
        }});

        ivyService.publish(publishRepositoriesDummy, moduleDummy, configurations, someDescriptorDestination);
    }

    private IvyBackedArtifactPublisher createIvyService() {
        return new IvyBackedArtifactPublisher(
                settingsConverterStub,
                publishModuleDescriptorConverter,
                ivyFactoryStub,
                ivyDependencyPublisherMock);
    }

    private Set<Configuration> createConfiguration() {
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
}
