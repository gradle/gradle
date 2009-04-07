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
import java.util.Map;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultArtifactsToModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testAddArtifacts() {
        DefaultArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter =
                new DefaultArtifactsToModuleDescriptorConverter();
        Configuration configurationStub1 = createConfigurationStub("conf1");
        Configuration configurationStub2 = createConfigurationStub("conf2");
        DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(WrapUtil.toSet(configurationStub1.getName(),
                configurationStub2.getName()));

        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, WrapUtil.toSet(configurationStub1, configurationStub2));

        assertArtifactIsAdded(configurationStub1, moduleDescriptor);
        assertArtifactIsAdded(configurationStub2, moduleDescriptor);
        assertThat(moduleDescriptor.getAllArtifacts().length, equalTo(2));
    }

    private void assertArtifactIsAdded(Configuration configurationStub1, DefaultModuleDescriptor moduleDescriptor) {
        assertThat(moduleDescriptor.getArtifacts(configurationStub1.getName()),
                equalTo(WrapUtil.toArray(expectedIvyArtifact(configurationStub1, moduleDescriptor))));
    }

    private Artifact expectedIvyArtifact(Configuration configuration, ModuleDescriptor moduleDescriptor) {
        PublishArtifact publishArtifact = configuration.getArtifacts().iterator().next();
        Map<String,String> extraAttributes = WrapUtil.toMap(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        extraAttributes.put(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE, publishArtifact.getFile().getAbsolutePath());
        return new DefaultArtifact(moduleDescriptor.getModuleRevisionId(),
                publishArtifact.getDate(),
                publishArtifact.getName(),
                publishArtifact.getType(),
                publishArtifact.getExtension(),
                extraAttributes);
    }

    private Configuration createConfigurationStub(final String name) {
        final Configuration configurationStub = IvyConverterTestUtil.createNamedConfigurationStub(name, context);
        context.checking(new Expectations() {{
            allowing(configurationStub).getArtifacts();
            will(returnValue(WrapUtil.toSet(createNamedPublishArtifact(name))));
        }});
        return configurationStub;
    }

    private PublishArtifact createNamedPublishArtifact(String name) {
        return new DefaultPublishArtifact(name, "ext", "type", "classifier", new Date(), new File("somePath"));
    }


}
