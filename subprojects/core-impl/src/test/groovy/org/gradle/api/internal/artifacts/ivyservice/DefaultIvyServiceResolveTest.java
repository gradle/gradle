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

import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultIvyServiceResolveTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();

    private Module moduleDummy = context.mock(Module.class);
    private DependencyMetaDataProvider dependencyMetaDataProviderMock = context.mock(DependencyMetaDataProvider.class);
    private ResolverProvider resolverProvider = context.mock(ResolverProvider.class);
    private IvyFactory ivyFactoryStub = context.mock(IvyFactory.class);
    private ArtifactDependencyResolver artifactDependencyResolverMock = context.mock(ArtifactDependencyResolver.class);
    private SettingsConverter settingsConverterMock = context.mock(SettingsConverter.class);

    // SUT
    private DefaultIvyService ivyService;

    @Before
    public void setUp() {
        ModuleDescriptorConverter publishModuleDescriptorConverterDummy = context.mock(ModuleDescriptorConverter.class, "publish");

        context.checking(new Expectations() {{
            allowing(dependencyMetaDataProviderMock).getModule();
            will(returnValue(moduleDummy));
        }});

        ivyService = new DefaultIvyService(resolverProvider,
                settingsConverterMock, publishModuleDescriptorConverterDummy,
                publishModuleDescriptorConverterDummy,
                ivyFactoryStub, artifactDependencyResolverMock,
                context.mock(IvyDependencyPublisher.class));
    }

    @Test
    public void testResolve() {
        final ConfigurationInternal configurationDummy = context.mock(ConfigurationInternal.class);
        final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);

        context.checking(new Expectations() {{
            one(artifactDependencyResolverMock).resolve(configurationDummy);
            will(returnValue(resolvedConfiguration));
        }});

        assertThat(ivyService.resolve(configurationDummy), sameInstance(resolvedConfiguration));
    }
}
