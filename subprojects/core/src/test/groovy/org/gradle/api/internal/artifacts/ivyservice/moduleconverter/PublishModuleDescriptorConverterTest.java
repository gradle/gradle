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
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class PublishModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Test
    public void convert() {
        final Set<Configuration> configurationsDummy = WrapUtil.toSet(context.mock(Configuration.class, "conf1"),
                context.mock(Configuration.class, "conf2"));
        final Module moduleDummy = context.mock(Module.class);
        final IvySettings ivySettingsDummy = context.mock(IvySettings.class);
        final DefaultModuleDescriptor moduleDescriptorDummy = context.mock(DefaultModuleDescriptor.class);
        final ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter = context.mock(ArtifactsToModuleDescriptorConverter.class);
        final ModuleDescriptorConverter resolveModuleDescriptorConverter = context.mock(ModuleDescriptorConverter.class);
        PublishModuleDescriptorConverter publishModuleDescriptorConverter = new PublishModuleDescriptorConverter(
                resolveModuleDescriptorConverter,
                artifactsToModuleDescriptorConverter);

        context.checking(new Expectations() {{
            allowing(resolveModuleDescriptorConverter).convert(configurationsDummy, moduleDummy, ivySettingsDummy);
            will(returnValue(moduleDescriptorDummy));
            one(moduleDescriptorDummy).addExtraAttributeNamespace(PublishModuleDescriptorConverter.IVY_MAVEN_NAMESPACE_PREFIX,
                    PublishModuleDescriptorConverter.IVY_MAVEN_NAMESPACE);
            one(artifactsToModuleDescriptorConverter).addArtifacts(moduleDescriptorDummy, configurationsDummy);
        }});

        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
                publishModuleDescriptorConverter.convert(configurationsDummy, moduleDummy, ivySettingsDummy);

        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
    }
}