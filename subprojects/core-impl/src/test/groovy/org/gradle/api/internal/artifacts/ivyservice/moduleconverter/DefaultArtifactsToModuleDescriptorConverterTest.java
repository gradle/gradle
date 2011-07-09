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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactsToModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testAddArtifacts() {
        final PublishArtifact publishArtifactConf1 = createNamedPublishArtifact("conf1");
        Configuration configurationStub1 = createConfigurationStub(publishArtifactConf1);
        final PublishArtifact publishArtifactConf2 = createNamedPublishArtifact("conf2");
        Configuration configurationStub2 = createConfigurationStub(publishArtifactConf2);
        final ArtifactsExtraAttributesStrategy artifactsExtraAttributesStrategyMock = context.mock(ArtifactsExtraAttributesStrategy.class);
        final Map<String, String> extraAttributesArtifact1 = WrapUtil.toMap("name", publishArtifactConf1.getName());
        final Map<String, String> extraAttributesArtifact2 = WrapUtil.toMap("name", publishArtifactConf2.getName());
        context.checking(new Expectations() {{
            one(artifactsExtraAttributesStrategyMock).createExtraAttributes(publishArtifactConf1);
            will(returnValue(extraAttributesArtifact1));
            one(artifactsExtraAttributesStrategyMock).createExtraAttributes(publishArtifactConf2);
            will(returnValue(extraAttributesArtifact2));
        }});
        DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(WrapUtil.toSet(configurationStub1.getName(),
                configurationStub2.getName()));

        DefaultArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter =
                new DefaultArtifactsToModuleDescriptorConverter(artifactsExtraAttributesStrategyMock);

        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, WrapUtil.toSet(configurationStub1, configurationStub2));

        assertArtifactIsAdded(configurationStub1, moduleDescriptor, extraAttributesArtifact1);
        assertArtifactIsAdded(configurationStub2, moduleDescriptor, extraAttributesArtifact2);
        assertThat(moduleDescriptor.getAllArtifacts().length, equalTo(2));
    }

    @Test
    public void testIvyFileStrategy() {
        assertThat(
                DefaultArtifactsToModuleDescriptorConverter.IVY_FILE_STRATEGY.createExtraAttributes(context.mock(PublishArtifact.class)),
                equalTo((Map) new HashMap<String, String>()));
    }

    @Test
    public void testResolveStrategy() {
        PublishArtifact publishArtifact = createNamedPublishArtifact("someName");
        Map<String, String> expectedExtraAttributes = WrapUtil.toMap(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE, publishArtifact.getFile().getAbsolutePath());
        assertThat(
                DefaultArtifactsToModuleDescriptorConverter.RESOLVE_STRATEGY.createExtraAttributes(publishArtifact),
                equalTo(expectedExtraAttributes));
    }

    private void assertArtifactIsAdded(Configuration configuration, DefaultModuleDescriptor moduleDescriptor, Map<String, String> extraAttributes) {
        assertThat(moduleDescriptor.getArtifacts(configuration.getName()),
                equalTo(WrapUtil.toArray(expectedIvyArtifact(configuration, moduleDescriptor, extraAttributes))));
    }

    private Artifact expectedIvyArtifact(Configuration configuration, ModuleDescriptor moduleDescriptor, Map<String, String> additionalExtraAttributes) {
        PublishArtifact publishArtifact = configuration.getArtifacts().iterator().next();
        Map<String, String> extraAttributes = WrapUtil.toMap(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        extraAttributes.putAll(additionalExtraAttributes);
        return new DefaultArtifact(moduleDescriptor.getModuleRevisionId(),
                publishArtifact.getDate(),
                publishArtifact.getName(),
                publishArtifact.getType(),
                publishArtifact.getExtension(),
                extraAttributes);
    }

    private Configuration createConfigurationStub(final PublishArtifact publishArtifact) {
        final Configuration configurationStub = IvyConverterTestUtil.createNamedConfigurationStub(publishArtifact.getName(), context);
        context.checking(new Expectations() {{
            allowing(configurationStub).getArtifacts();
            will(returnValue(WrapUtil.toSet(publishArtifact)));
        }});
        return configurationStub;
    }

    private PublishArtifact createNamedPublishArtifact(String name) {
        return new DefaultPublishArtifact(name, "ext", "type", "classifier", new Date(), new File("somePath"));
    }


}
