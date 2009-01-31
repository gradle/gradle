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
package org.gradle.api.internal.dependencies.ivy.moduleconverter;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.dependencies.PublishArtifact;
import org.gradle.api.internal.dependencies.ArtifactContainer;
import org.gradle.api.internal.dependencies.DefaultConfiguration;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactsToModuleDescriptorConverterTest {
    private DefaultArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter;
    private ArtifactContainer artifactContainerMock;
    private PublishArtifact publishArtifactMock;
    private Artifact ivyArtifact;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        ivyArtifact = context.mock(Artifact.class);
        artifactContainerMock = context.mock(ArtifactContainer.class);
        publishArtifactMock = context.mock(PublishArtifact.class);
        artifactsToModuleDescriptorConverter = new DefaultArtifactsToModuleDescriptorConverter();
    }

    @Test
    public void testAddArtifacts() {
        String confName1 = "conf1";
        String confName2 = "conf2";
        final DefaultModuleDescriptor moduleDescriptor = HelperUtil.getTestModuleDescriptor(WrapUtil.toSet(confName1, confName2));
        final Set<DefaultConfiguration> testConfs = HelperUtil.createConfigurations(confName1, confName2);
        context.checking(new Expectations() {{
            allowing(artifactContainerMock).getArtifacts(HelperUtil.TEST_SEPC);
            will(returnValue(WrapUtil.toSet(publishArtifactMock)));

            allowing(publishArtifactMock).getConfigurations();
            will(returnValue(testConfs));

            allowing(publishArtifactMock).createIvyArtifact(moduleDescriptor.getModuleRevisionId());
            will(returnValue(ivyArtifact));
        }});
        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, artifactContainerMock, HelperUtil.TEST_SEPC);
        assertThat(moduleDescriptor.getArtifacts(confName1), equalTo(WrapUtil.toArray(ivyArtifact)));
        assertThat(moduleDescriptor.getArtifacts(confName2), equalTo(WrapUtil.toArray(ivyArtifact)));
        assertThat(moduleDescriptor.getAllArtifacts().length, equalTo(1));
    }

    
}
