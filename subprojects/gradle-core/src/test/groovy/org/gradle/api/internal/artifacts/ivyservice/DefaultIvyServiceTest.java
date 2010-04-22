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

import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyServiceTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Test
    public void init() {
        DependencyMetaDataProvider dependencyMetaDataProvider = context.mock(DependencyMetaDataProvider.class);
        ResolverProvider resolverProvider = context.mock(ResolverProvider.class);
        SettingsConverter settingsConverter = context.mock(SettingsConverter.class);
        ModuleDescriptorConverter resolveModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "resolve");
        ModuleDescriptorConverter publishModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "publish");
        ModuleDescriptorConverter fileModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class, "file");
        IvyFactory ivyFactory = context.mock(IvyFactory.class);
        IvyDependencyResolver dependencyResolver = context.mock(IvyDependencyResolver.class);
        IvyDependencyPublisher dependencyPublisher = context.mock(IvyDependencyPublisher.class);
        Map clientModuleRegistry = new HashMap();

        DefaultIvyService ivyService = new DefaultIvyService(dependencyMetaDataProvider, resolverProvider,
                settingsConverter, resolveModuleDescriptorConverter, publishModuleDescriptorConverter,
                fileModuleDescriptorConverter,
                ivyFactory, dependencyResolver, dependencyPublisher, clientModuleRegistry);
        
        assertThat(ivyService.getMetaDataProvider(), sameInstance(dependencyMetaDataProvider));
        assertThat(ivyService.getSettingsConverter(), sameInstance(settingsConverter));
        assertThat(ivyService.getResolveModuleDescriptorConverter(), sameInstance(resolveModuleDescriptorConverter));
        assertThat(ivyService.getPublishModuleDescriptorConverter(), sameInstance(publishModuleDescriptorConverter));
        assertThat(ivyService.getFileModuleDescriptorConverter(), sameInstance(fileModuleDescriptorConverter));
        assertThat(ivyService.getIvyFactory(), sameInstance(ivyFactory));
        assertThat(ivyService.getDependencyResolver(), sameInstance(dependencyResolver));
        assertThat(ivyService.getDependencyPublisher(), sameInstance(dependencyPublisher));
        assertThat(ivyService.getClientModuleRegistry(), sameInstance(clientModuleRegistry));
    }
}
