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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class DefaultConfigurationsToModuleDescriptorConverterTest {
    private DefaultConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter = new DefaultConfigurationsToModuleDescriptorConverter();

    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testAddConfigurations() {
        Configuration configurationStub1 = createNamesAndExtendedConfigurationStub("conf1");
        Configuration configurationStub2 = createNamesAndExtendedConfigurationStub("conf2", configurationStub1);
        final DefaultModuleDescriptor moduleDescriptor = HelperUtil.createModuleDescriptor(Collections.EMPTY_SET);

        configurationsToModuleDescriptorConverter.addConfigurations(moduleDescriptor, WrapUtil.toSet(configurationStub1, configurationStub2));

        assertIvyConfigurationIsCorrect(moduleDescriptor.getConfiguration(configurationStub1.getName()),
                expectedIvyConfiguration(configurationStub1));
        assertIvyConfigurationIsCorrect(moduleDescriptor.getConfiguration(configurationStub2.getName()),
                expectedIvyConfiguration(configurationStub2));
        assertThat(moduleDescriptor.getConfigurations().length, equalTo(2));
    }

    private void assertIvyConfigurationIsCorrect(org.apache.ivy.core.module.descriptor.Configuration actualConfiguration,
                                                 org.apache.ivy.core.module.descriptor.Configuration expectedConfiguration) {
        assertThat(actualConfiguration.getDescription(), equalTo(expectedConfiguration.getDescription()));
        assertThat(actualConfiguration.isTransitive(), equalTo(expectedConfiguration.isTransitive()));
        assertThat(actualConfiguration.getVisibility(), equalTo(expectedConfiguration.getVisibility()));
        assertThat(actualConfiguration.getName(), equalTo(expectedConfiguration.getName()));
        assertThat(actualConfiguration.getExtends(), equalTo(expectedConfiguration.getExtends()));
    }

    private org.apache.ivy.core.module.descriptor.Configuration expectedIvyConfiguration(Configuration configuration) {
        return new org.apache.ivy.core.module.descriptor.Configuration(
                configuration.getName(),
                configuration.isVisible() ? org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC : org.apache.ivy.core.module.descriptor.Configuration.Visibility.PRIVATE,
                configuration.getDescription(),
                Configurations.getNames(configuration.getExtendsFrom(), false).toArray(new String[configuration.getExtendsFrom().size()]),
                configuration.isTransitive(),
                null);
    }

    private Configuration createNamesAndExtendedConfigurationStub(final String name, final Configuration... extendsFromConfigurations) {
        final Configuration configurationStub = IvyConverterTestUtil.createNamedConfigurationStub(name, context);
        context.checking(new Expectations() {{
            allowing(configurationStub).isTransitive();
            will(returnValue(true));

            allowing(configurationStub).getDescription();
            will(returnValue(HelperUtil.createUniqueId()));

            allowing(configurationStub).isVisible();
            will(returnValue(true));

            allowing(configurationStub).getExtendsFrom();
            will(returnValue(WrapUtil.toSet(extendsFromConfigurations)));
        }});
        return configurationStub;
    }
}
