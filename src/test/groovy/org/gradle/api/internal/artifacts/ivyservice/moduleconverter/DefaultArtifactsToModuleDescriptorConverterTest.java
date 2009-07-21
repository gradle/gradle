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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.ivyservice.IvyArtifactFilePathVariableProvider;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactsToModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);   
    }};

    @Before
    public void setUp() {
           
    }

    @Test
    public void testAddArtifacts() {
        DefaultArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter =
                new DefaultArtifactsToModuleDescriptorConverter();
        final PublishArtifact publishArtifact1 = createNamedPublishArtifact("name1");
        final PublishArtifact publishArtifact2 = createNamedPublishArtifact("name2");
        final Configuration configurationStub1 = createConfigurationStub("conf1", publishArtifact1);
        Configuration configurationStub2 = createConfigurationStub("conf2", publishArtifact2);
        DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(WrapUtil.toSet(configurationStub1.getName(),
                configurationStub2.getName()));
        final Artifact ivyArtifact1 = expectedIvyArtifact(publishArtifact1, moduleDescriptor);
        final Artifact ivyArtifact2 = expectedIvyArtifact(publishArtifact2, moduleDescriptor);
        final IvyArtifactFilePathVariableProvider filePathVariableProviderStub = context.mock(IvyArtifactFilePathVariableProvider.class);
        final String ivyArtifact1VariableName = "ivyArtifact1Variable";
        final String ivyArtifact2VariableName = "ivyArtifact2Variable";
        final IvySettings ivySettingsMock = context.mock(IvySettings.class);
        context.checking(new Expectations() {{
            allowing(filePathVariableProviderStub).createVariableName(ivyArtifact1);
            will(returnValue(ivyArtifact1VariableName));

            allowing(filePathVariableProviderStub).createVariableName(ivyArtifact2);
            will(returnValue(ivyArtifact2VariableName));

            one(ivySettingsMock).setVariable(ivyArtifact1VariableName, publishArtifact1.getFile().getAbsolutePath());
            one(ivySettingsMock).setVariable(ivyArtifact2VariableName, publishArtifact2.getFile().getAbsolutePath());
        }});

        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, WrapUtil.toSet(configurationStub1, configurationStub2),
                ivySettingsMock, filePathVariableProviderStub);

        assertArtifactIsAdded(configurationStub1, moduleDescriptor, ivyArtifact1);
        assertArtifactIsAdded(configurationStub2, moduleDescriptor, ivyArtifact2);
        assertThat(moduleDescriptor.getAllArtifacts().length, equalTo(2));
    }

    private void assertArtifactIsAdded(Configuration configurationStub1, DefaultModuleDescriptor moduleDescriptor, Artifact ivyArtifact) {
        assertThat(moduleDescriptor.getArtifacts(configurationStub1.getName()),
                equalTo(WrapUtil.toArray(ivyArtifact)));
    }

    private Artifact expectedIvyArtifact(PublishArtifact publishArtifact, ModuleDescriptor moduleDescriptor) {
        Map<String,String> extraAttributes = WrapUtil.toMap(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        return new DefaultArtifact(moduleDescriptor.getModuleRevisionId(),
                publishArtifact.getDate(),
                publishArtifact.getName(),
                publishArtifact.getType(),
                publishArtifact.getExtension(),
                extraAttributes);
    }

    private Configuration createConfigurationStub(final String name, final PublishArtifact publishArtifact) {
        final Configuration configurationStub = IvyConverterTestUtil.createNamedConfigurationStub(name, context);
        context.checking(new Expectations() {{
            allowing(configurationStub).getArtifacts();
            will(returnValue(WrapUtil.toSet(publishArtifact)));
        }});
        return configurationStub;
    }

    private PublishArtifact createNamedPublishArtifact(String name) {
        return new DefaultPublishArtifact(name, "ext", "type", "classifier", new Date(), new File("pathFor" + name));
    }
}
