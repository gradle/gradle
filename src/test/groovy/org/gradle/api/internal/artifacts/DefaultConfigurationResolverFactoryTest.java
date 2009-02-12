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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolverContainer;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultConfigurationResolverFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private IvyService ivyServiceMock = context.mock(IvyService.class);
    private File gradleUserHomeFile = new File("gradleUserHome");
    private final Configuration configurationMock = context.mock(Configuration.class);
    private final DependencyContainerInternal dependencyContainerMock = context.mock(DependencyContainerInternal.class);
    private final ArtifactContainer artifactContainerMock = context.mock(ArtifactContainer.class);
    private final ConfigurationContainer publishConfigurationContainerMock = context.mock(ConfigurationContainer.class);
    private final ResolverContainer resolverContainerMock = context.mock(ResolverContainer.class);

    private DefaultConfigurationResolverFactory configurationResolverFactory = new DefaultConfigurationResolverFactory(
            ivyServiceMock, gradleUserHomeFile
    );

    @Test
    public void testCreateConfigurationResolver() {
        DefaultConfigurationResolver configurationResolver = (DefaultConfigurationResolver)
                configurationResolverFactory.createConfigurationResolver(configurationMock, dependencyContainerMock,
                        resolverContainerMock, artifactContainerMock, publishConfigurationContainerMock);
        assertThat(configurationResolver.getWrappedConfiguration(), sameInstance(configurationMock));
        assertThat(configurationResolver.getArtifactContainer(), sameInstance(artifactContainerMock));
        assertThat(configurationResolver.getDependencyContainer(), sameInstance(dependencyContainerMock));
        assertThat(configurationResolver.getDependencyResolvers(), sameInstance(resolverContainerMock));
        assertThat(configurationResolver.getGradleUserHome(), sameInstance(gradleUserHomeFile));
        assertThat(configurationResolver.getIvyHandler(), sameInstance(ivyServiceMock));
        assertThat(configurationResolver.getPublishConfigurationContainer(), sameInstance(publishConfigurationContainerMock));
    }
}
