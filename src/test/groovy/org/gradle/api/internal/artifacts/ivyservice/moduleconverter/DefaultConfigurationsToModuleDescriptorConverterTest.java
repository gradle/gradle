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
import org.gradle.api.internal.artifacts.ConfigurationContainer;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultConfigurationsToModuleDescriptorConverterTest {
    private DefaultConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter = new DefaultConfigurationsToModuleDescriptorConverter();
    private ConfigurationContainer configurationContainerMock;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testAddConfigurations() {
        String confName1 = "conf1";
        String confName2 = "conf2";
        String confName3 = "conf3";
        String confName4 = "conf4";
        org.apache.ivy.core.module.descriptor.Configuration ivyConf1 = new org.apache.ivy.core.module.descriptor.Configuration(confName1);
        org.apache.ivy.core.module.descriptor.Configuration ivyConf2 = new org.apache.ivy.core.module.descriptor.Configuration(confName2);
        org.apache.ivy.core.module.descriptor.Configuration ivyConf3 = new org.apache.ivy.core.module.descriptor.Configuration(confName3);
        org.apache.ivy.core.module.descriptor.Configuration ivyConf4 = new org.apache.ivy.core.module.descriptor.Configuration(confName4);
        final Set<Configuration> testConfs = WrapUtil.toSet(
                createMockConf(confName1, ivyConf1, false, true),
                createMockConf(confName2, ivyConf2, true, false),
                createMockConf(confName3, ivyConf3, true, true),
                createMockConf(confName4, ivyConf4, false, false));
        configurationContainerMock = context.mock(ConfigurationContainer.class);
        final DefaultModuleDescriptor moduleDescriptor = HelperUtil.getTestModuleDescriptor(Collections.EMPTY_SET);
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).get(HelperUtil.TEST_SEPC);
            will(returnValue(testConfs));
        }});
        Map<String, Boolean> transitiveOverride = GUtil.map(confName1, true, confName2, false);
        configurationsToModuleDescriptorConverter.addConfigurations(moduleDescriptor, configurationContainerMock, HelperUtil.TEST_SEPC, transitiveOverride);
        assertEquals(WrapUtil.toSet(ivyConf1, ivyConf2, ivyConf3, ivyConf4),
                new HashSet(Arrays.asList(moduleDescriptor.getConfigurations())));
    }

    private Configuration createMockConf(final String confName, final org.apache.ivy.core.module.descriptor.Configuration ivyConf,
                                         final boolean defaultTransitive, final boolean actualTransitive) {
        final Configuration configurationMock = context.mock(Configuration.class, confName);
        context.checking(new Expectations() {{
            allowing(configurationMock).getName();
            will(returnValue(confName));

            allowing(configurationMock).isTransitive();
            will(returnValue(defaultTransitive));

            allowing(configurationMock).getIvyConfiguration(actualTransitive);
            will(returnValue(ivyConf));
        }});
        return configurationMock;
    }
}
