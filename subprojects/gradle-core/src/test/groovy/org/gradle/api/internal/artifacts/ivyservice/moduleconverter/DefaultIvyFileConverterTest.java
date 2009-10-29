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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
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
public class DefaultIvyFileConverterTest {
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
        final ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter = context.mock(ArtifactsToModuleDescriptorConverter.class);

        DefaultIvyFileConverter ivyFileConverter = new DefaultIvyFileConverter(
                moduleDescriptorFactoryStub,
                configurationsToModuleDescriptorConverterMock,
                dependenciesToModuleDescriptorConverterMock,
                artifactsToModuleDescriptorConverter);

        ivyFileConverter.addIvyTransformer(createTransformerMock(moduleDescriptorDummy));

        context.checking(new Expectations() {{
            for (Configuration configuration : configurationsDummy) {
                allowing(configuration).getAll(); will(returnValue(configurationsDummy));
            }
            allowing(moduleDescriptorFactoryStub).createModuleDescriptor(moduleDummy);
            will(returnValue(moduleDescriptorDummy));
            one(configurationsToModuleDescriptorConverterMock).addConfigurations(moduleDescriptorDummy, configurationsDummy);
            one(dependenciesToModuleDescriptorConverterMock).addDependencyDescriptors(moduleDescriptorDummy, configurationsDummy, ivySettingsDummy);
            one(artifactsToModuleDescriptorConverter).addArtifacts(moduleDescriptorDummy, configurationsDummy);
        }});

        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
                ivyFileConverter.convert(configurationsDummy, moduleDummy, ivySettingsDummy);

        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
    }

    private Transformer createTransformerMock(final DefaultModuleDescriptor moduleDescriptor) {
        final Transformer transformer = context.mock(Transformer.class);
        context.checking(new Expectations() {{
            one(transformer).transform(moduleDescriptor);
            will(returnValue(moduleDescriptor));
        }});
        return transformer;
    }


//    @Before
//    public void setUp() {
//        ModuleDescriptorFactory moduleDescriptorFactoryStub = context.mock(ModuleDescriptorFactory.class);
//        ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverterMock = context.mock(ConfigurationsToModuleDescriptorConverter.class);
//        DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverterMock = context.mock(DependenciesToModuleDescriptorConverter.class);
//
//        moduleDescriptorConverter = new ResolveModuleDescriptorConverter(
//                moduleDescriptorFactoryStub,
//                configurationsToModuleDescriptorConverterMock,
//                dependenciesToModuleDescriptorConverterMock,
//                context.mock(ArtifactsToModuleDescriptorConverter.class, "dummy"));
//    }
//
//    @Test
//    public void convertForPublishWithoutDescriptor() {
//        convertForPublishInternal(configurationsDummy, false);
//    }
//
//    @Test
//    public void convertForPublishWithDescriptor() {
//        context.checking(new Expectations() {{
//            allowing(configurationsDummy.iterator().next()).getAll();
//            will(returnValue(configurationsDummy));
//        }});
//        convertForPublishInternal(configurationsDummy, true);
//    }
//
//    @Test
//    public void convertForPublishWithTransformer() {
//        createIvyTransformerExpectations();
//        convertForPublishInternal(configurationsDummy, false);
//    }
//
//    private void convertForPublishInternal(final Set<Configuration> configurations, boolean publishDescriptor) {
//        commonSetUp();
//        moduleDescriptorConverter.setArtifactsToModuleDescriptorConverter(context.mock(ArtifactsToModuleDescriptorConverter.class));
//
//        defineCommonExpectations(configurations);
//        context.checking(new Expectations() {{
//            one(moduleDescriptorConverter.getArtifactsToModuleDescriptorConverter()).
//                    addArtifacts(moduleDescriptorDummy, configurations);
//        }});
//
//        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
//                moduleDescriptorConverter.convertForPublish(configurations, publishDescriptor, moduleDummy, ivySettingsDummy);
//
//        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
//    }
//
//    @Test
//    public void convertForResolve() {
//        convertForResolveInternal();
//    }
//
//    @Test
//    public void convertForResolveWithTransformer() {
//        createIvyTransformerExpectations();
//        convertForResolveInternal();
//    }
//
//    private void convertForResolveInternal() {
//        final Configuration configurationStub = context.mock(Configuration.class, "conf");
//        context.checking(new Expectations() {{
//            allowing(configurationStub).getHierarchy();
//            will(returnValue(new ArrayList(configurationsDummy)));
//        }});
//        commonSetUp();
//
//        defineCommonExpectations(configurationsDummy);
//
//        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
//                moduleDescriptorConverter.convertForResolve(configurationStub, moduleDummy, ivySettingsDummy);
//
//        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
//    }
//
//    private void commonSetUp() {
//        context.checking(new Expectations() {{
//            allowing(moduleDescriptorConverter.getModuleDescriptorFactory()).createModuleDescriptor(moduleDummy);
//            will(returnValue(moduleDescriptorDummy));
//        }});
//    }
//
//    private void defineCommonExpectations(final Set<Configuration> configurationsDummy) {
//        context.checking(new Expectations() {{
//            one(moduleDescriptorConverter.getConfigurationsToModuleDescriptorConverter()).
//                    addConfigurations(moduleDescriptorDummy, configurationsDummy);
//
//            one(moduleDescriptorConverter.getDependenciesToModuleDescriptorConverter()).
//                    addDependencyDescriptors(moduleDescriptorDummy, configurationsDummy, ivySettingsDummy);
//        }});
//    }
//
//    private void createIvyTransformerExpectations() {
//        moduleDescriptorConverter.addIvyTransformer(createTransformerMock());
//    }
//
//    private Transformer createTransformerMock() {
//        final Transformer transformer = context.mock(Transformer.class);
//        context.checking(new Expectations() {{
//            one(transformer).transform(moduleDescriptorDummy);
//            will(returnValue(moduleDescriptorDummy));
//        }});
//        return transformer;
//    }
}