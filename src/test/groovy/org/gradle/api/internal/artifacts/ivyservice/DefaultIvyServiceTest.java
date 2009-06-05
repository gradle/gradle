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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultModuleDescriptorConverter;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyServiceTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    // SUT
    private DefaultIvyService ivyService;
    private DependencyMetaDataProvider dependencyMetaDataProvider;

    @Before
    public void setUp() {
        dependencyMetaDataProvider = context.mock(DependencyMetaDataProvider.class);
        ivyService = new DefaultIvyService(dependencyMetaDataProvider, context.mock(ResolverProvider.class));
    }

    @Test
    public void init() {
        assertThat(ivyService.getMetaDataProvider(), sameInstance(dependencyMetaDataProvider));
        assertThat(ivyService.getSettingsConverter(), instanceOf(DefaultSettingsConverter.class));
        assertThat(ivyService.getModuleDescriptorConverter(), instanceOf(DefaultModuleDescriptorConverter.class));
        assertThat(ivyService.getIvyFactory(), instanceOf(DefaultIvyFactory.class));
        assertThat(ivyService.getDependencyResolver(), instanceOf(SelfResolvingDependencyResolver.class));
        SelfResolvingDependencyResolver resolver = (SelfResolvingDependencyResolver) ivyService.getDependencyResolver();
        assertThat(resolver.getResolver(), instanceOf(DefaultIvyDependencyResolver.class));
        assertThat(ivyService.getDependencyPublisher(), instanceOf(DefaultIvyDependencyPublisher.class));
    }
}
