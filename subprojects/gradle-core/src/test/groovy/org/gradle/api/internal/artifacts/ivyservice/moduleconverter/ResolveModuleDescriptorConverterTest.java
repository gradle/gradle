/*
 * Copyright 2007 the original author or authors.
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
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class ResolveModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Test
    public void convert() {
        final Set<Configuration> configurationsDummy = WrapUtil.toSet(context.mock(Configuration.class, "conf1"),
                context.mock(Configuration.class, "conf2"));
        final Module moduleDummy = context.mock(Module.class);
        final IvySettings ivySettingsDummy = new IvySettings();
        final DefaultModuleDescriptor moduleDescriptorDummy = context.mock(DefaultModuleDescriptor.class);
        final ModuleDescriptorFactory moduleDescriptorFactoryStub = context.mock(ModuleDescriptorFactory.class);
        final ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverterMock = context.mock(ConfigurationsToModuleDescriptorConverter.class);
        final DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverterMock = context.mock(DependenciesToModuleDescriptorConverter.class);

        ResolveModuleDescriptorConverter resolveModuleDescriptorConverter = new ResolveModuleDescriptorConverter(
                moduleDescriptorFactoryStub,
                configurationsToModuleDescriptorConverterMock,
                dependenciesToModuleDescriptorConverterMock);

        context.checking(new Expectations() {{
            allowing(moduleDescriptorFactoryStub).createModuleDescriptor(moduleDummy);
            will(returnValue(moduleDescriptorDummy));
            one(configurationsToModuleDescriptorConverterMock).addConfigurations(moduleDescriptorDummy, configurationsDummy);
            one(dependenciesToModuleDescriptorConverterMock).addDependencyDescriptors(moduleDescriptorDummy, configurationsDummy, ivySettingsDummy);
        }});

        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
                resolveModuleDescriptorConverter.convert(configurationsDummy, moduleDummy, ivySettingsDummy);

        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
    }
}
