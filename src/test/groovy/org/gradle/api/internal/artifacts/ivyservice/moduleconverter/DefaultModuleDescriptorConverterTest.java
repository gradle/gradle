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
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultModuleDescriptorConverterTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    // Dummies
    private Set<Configuration> configurationsDummy = WrapUtil.toSet(context.mock(Configuration.class, "conf1"),
            context.mock(Configuration.class, "conf2"));
    private Module moduleDummy = context.mock(Module.class);
    private DefaultModuleDescriptor moduleDescriptorDummy = context.mock(DefaultModuleDescriptor.class);

    // SUT
    private DefaultModuleDescriptorConverter moduleDescriptorConverter;

    @Before
    public void setUp() {
        ModuleDescriptorFactory moduleDescriptorFactoryStub = context.mock(ModuleDescriptorFactory.class);
        ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverterMock = context.mock(ConfigurationsToModuleDescriptorConverter.class);
        DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverterMock = context.mock(DependenciesToModuleDescriptorConverter.class);

        moduleDescriptorConverter = new DefaultModuleDescriptorConverter();
        moduleDescriptorConverter.setConfigurationsToModuleDescriptorConverter(configurationsToModuleDescriptorConverterMock);
        moduleDescriptorConverter.setDependenciesToModuleDescriptorConverter(dependenciesToModuleDescriptorConverterMock);
        moduleDescriptorConverter.setModuleDescriptorFactory(moduleDescriptorFactoryStub);
    }

    @Test
    public void convertForPublishWithoutDescriptor() {
        convertForPublishInternal(configurationsDummy, false);
    }

    @Test
    public void convertForPublishWithDescriptor() {
        context.checking(new Expectations() {{
            allowing(configurationsDummy.iterator().next()).getAll();
            will(returnValue(configurationsDummy));
        }});
        convertForPublishInternal(configurationsDummy, true);
    }

    @Test
    public void convertForPublishWithTransformer() {
        createIvyTransformerExpectations();
        convertForPublishInternal(configurationsDummy, false);
    }

    private void convertForPublishInternal(final Set<Configuration> configurations, boolean publishDescriptor) {
        commonSetUp();
        moduleDescriptorConverter.setArtifactsToModuleDescriptorConverter(context.mock(ArtifactsToModuleDescriptorConverter.class));

        defineCommonExpectations(configurations, new HashMap());
        context.checking(new Expectations() {{
            one(moduleDescriptorConverter.getArtifactsToModuleDescriptorConverter()).
                    addArtifacts(moduleDescriptorDummy, configurations);
        }});

        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
                moduleDescriptorConverter.convertForPublish(configurations, publishDescriptor, moduleDummy);

        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
    }

    @Test
    public void convertForResolve() {
        convertForResolveInternal();
    }

    @Test
    public void convertForResolveWithTransformer() {
        createIvyTransformerExpectations();
        convertForResolveInternal();
    }

    private void convertForResolveInternal() {
        final Configuration configurationStub = context.mock(Configuration.class, "conf");
        Map moduleRegistryDummy = WrapUtil.toMap("key", context.mock(ModuleDescriptor.class));
        context.checking(new Expectations() {{
            allowing(configurationStub).getHierarchy();
            will(returnValue(new ArrayList(configurationsDummy)));
        }});
        commonSetUp();

        defineCommonExpectations(configurationsDummy, moduleRegistryDummy);

        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor)
                moduleDescriptorConverter.convertForResolve(configurationStub, moduleDummy, moduleRegistryDummy);

        assertThat(actualModuleDescriptor, equalTo(moduleDescriptorDummy));
    }

    private void commonSetUp() {
        context.checking(new Expectations() {{
            allowing(moduleDescriptorConverter.getModuleDescriptorFactory()).createModuleDescriptor(moduleDummy);
            will(returnValue(moduleDescriptorDummy));
        }});
    }

    private void defineCommonExpectations(final Set<Configuration> configurationsDummy, final Map clientModuleRegistry) {
        context.checking(new Expectations() {{
            one(moduleDescriptorConverter.getConfigurationsToModuleDescriptorConverter()).
                    addConfigurations(moduleDescriptorDummy, configurationsDummy);

            one(moduleDescriptorConverter.getDependenciesToModuleDescriptorConverter()).
                    addDependencyDescriptors(moduleDescriptorDummy, configurationsDummy, clientModuleRegistry);
        }});
    }

    private void createIvyTransformerExpectations() {
        moduleDescriptorConverter.addIvyTransformer(createTransformerMock());
    }

    private Transformer createTransformerMock() {
        final Transformer transformer = context.mock(Transformer.class);
        context.checking(new Expectations() {{
            one(transformer).transform(moduleDescriptorDummy);
            will(returnValue(moduleDescriptorDummy));
        }});
        return transformer;
    }
}
