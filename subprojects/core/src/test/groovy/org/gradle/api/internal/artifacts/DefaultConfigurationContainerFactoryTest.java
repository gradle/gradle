/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.ivyservice.*;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultConfigurationContainerFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testCreate() {
        ResolverProvider resolverProviderDummy = context.mock(ResolverProvider.class);
        final DependencyMetaDataProvider dependencyMetaDataProviderStub = context.mock(DependencyMetaDataProvider.class);

        HashMap clientModuleRegistry = new HashMap();
        SettingsConverter settingsConverter = context.mock(SettingsConverter.class);
        ModuleDescriptorConverter resolveModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "resolve");
        ModuleDescriptorConverter publishModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "publish");
        ModuleDescriptorConverter fileModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "file");
        IvyFactory ivyFactory = context.mock(IvyFactory.class);
        IvyDependencyResolver ivyDependencyResolver = context.mock(IvyDependencyResolver.class);
        IvyDependencyPublisher ivyDependencyPublisher = context.mock(IvyDependencyPublisher.class);
        ClassGenerator classGenerator = new AsmBackedClassGenerator();
        DefaultConfigurationContainer configurationContainer = (DefaultConfigurationContainer)
                new DefaultConfigurationContainerFactory(clientModuleRegistry, settingsConverter,
                        resolveModuleDescriptorConverter, publishModuleDescriptorConverter,
                        fileModuleDescriptorConverter, ivyFactory,
                        ivyDependencyResolver, ivyDependencyPublisher, classGenerator).createConfigurationContainer(resolverProviderDummy,
                        dependencyMetaDataProviderStub, context.mock(DomainObjectContext.class));

        assertThat(configurationContainer.getIvyService(), instanceOf(ErrorHandlingIvyService.class));
        ErrorHandlingIvyService errorHandlingService = (ErrorHandlingIvyService) configurationContainer.getIvyService();

        assertThat(errorHandlingService.getIvyService(), instanceOf(ShortcircuitEmptyConfigsIvyService.class));
        ShortcircuitEmptyConfigsIvyService service = (ShortcircuitEmptyConfigsIvyService) errorHandlingService.getIvyService();

        assertThat(service.getIvyService(), instanceOf(DefaultIvyService.class));
        DefaultIvyService defaultIvyService = (DefaultIvyService) service.getIvyService();
        assertThat(defaultIvyService.getMetaDataProvider(), sameInstance(dependencyMetaDataProviderStub));
        assertThat(defaultIvyService.getResolverProvider(), sameInstance(resolverProviderDummy));
        assertThat((HashMap) defaultIvyService.getClientModuleRegistry(), sameInstance(clientModuleRegistry));
        assertThat(defaultIvyService.getSettingsConverter(), sameInstance(settingsConverter));
        assertThat(defaultIvyService.getResolveModuleDescriptorConverter(), sameInstance(resolveModuleDescriptorConverter));
        assertThat(defaultIvyService.getPublishModuleDescriptorConverter(), sameInstance(publishModuleDescriptorConverter));
        assertThat(defaultIvyService.getFileModuleDescriptorConverter(), sameInstance(fileModuleDescriptorConverter));
        assertThat(defaultIvyService.getIvyFactory(), sameInstance(ivyFactory));
        assertThat(defaultIvyService.getDependencyResolver(), sameInstance(ivyDependencyResolver));
        assertThat(defaultIvyService.getDependencyPublisher(), sameInstance(ivyDependencyPublisher));
    }
}
