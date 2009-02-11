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

package org.gradle.api.internal.dependencies.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.PublishArtifact;
import org.gradle.api.dependencies.specs.ConfigurationSpec;
import org.gradle.api.internal.dependencies.ArtifactContainer;
import org.gradle.api.internal.dependencies.ConfigurationContainer;
import org.gradle.api.internal.dependencies.DependencyContainerInternal;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultModuleDescriptorConverterTest {
    private static final Spec<Dependency> TEST_DEPENDENCY_SPEC = new ConfigurationSpec<Dependency>(true, "conf1");
    private static final Spec<PublishArtifact> TEST_PUBLISH_SPEC = new AndSpec<PublishArtifact>();
    private static final Spec<Configuration> TEST_CONFIGURATION_SPEC = new AndSpec<Configuration>();

    private static final String TEST_STATUS = "testStatus";

    private DefaultModuleDescriptorConverter defaultModuleDescriptorConverter;
    private ModuleDescriptorFactory moduleDescriptorFactoryMock;
    private ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter;
    private DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverterMock;
    private ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverterMock;

    private DependencyContainerInternal dependencyContainerInternalMock;

    private ConfigurationContainer configurationContainerMock;
    private ArtifactContainer artifactContainerMock;
    private ModuleRevisionId expectedModuleRevisionId;
    private Project testProject;
    private DefaultModuleDescriptor expectedModuleDescriptor;

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Before
    public void setUp() {
        createFixture();
        defaultModuleDescriptorConverter = new DefaultModuleDescriptorConverter(moduleDescriptorFactoryMock,
                configurationsToModuleDescriptorConverter,
                dependenciesToModuleDescriptorConverterMock,
                artifactsToModuleDescriptorConverterMock);
    }

    private void createFixture() {
        createMocks();
        testProject = HelperUtil.createRootProject();
        expectedModuleDescriptor = context.mock(DefaultModuleDescriptor.class);
        context.checking(new Expectations() {{
            allowing(dependencyContainerInternalMock).getProject();
            will(returnValue(testProject));
        }});
        expectedModuleRevisionId = ModuleRevisionId.newInstance(testProject.getGroup().toString(), testProject.getName(), testProject.getVersion().toString());
    }

    private void createMocks() {
        configurationContainerMock = context.mock(ConfigurationContainer.class);
        dependencyContainerInternalMock = context.mock(DependencyContainerInternal.class);
        artifactContainerMock = context.mock(ArtifactContainer.class);
        moduleDescriptorFactoryMock = context.mock(ModuleDescriptorFactory.class);
        configurationsToModuleDescriptorConverter = context.mock(ConfigurationsToModuleDescriptorConverter.class);
        artifactsToModuleDescriptorConverterMock = context.mock(ArtifactsToModuleDescriptorConverter.class);
        dependenciesToModuleDescriptorConverterMock = context.mock(DependenciesToModuleDescriptorConverter.class);
    }

    @Test
    public void convert() {
        testProject.setProperty("status", TEST_STATUS);
        checkConversion(TEST_STATUS, null);
    }

    @Test
    public void convertWithDefaultStatus() {
        checkConversion(DependencyManager.DEFAULT_STATUS, null);
    }

    @Test
    public void convertWithTransform() {
        final boolean[] called = new boolean[]{false};
        checkConversion(DependencyManager.DEFAULT_STATUS, new Transformer<DefaultModuleDescriptor>() {
            public DefaultModuleDescriptor transform(DefaultModuleDescriptor original) {
                called[0] = true;
                return original;
            }
        });
        assertThat(called[0], Matchers.equalTo(true));
    }

    private DefaultModuleDescriptor checkConversion(final String status, Transformer<DefaultModuleDescriptor> transformer) {
        final Map<String,Boolean> testTransitiveOverride = WrapUtil.toMap("somename", true);
        context.checking(new Expectations() {{
            allowing(moduleDescriptorFactoryMock).createModuleDescriptor(expectedModuleRevisionId, status, null);
            will(returnValue(expectedModuleDescriptor));

            one(configurationsToModuleDescriptorConverter).addConfigurations(expectedModuleDescriptor, configurationContainerMock,
                    TEST_CONFIGURATION_SPEC, testTransitiveOverride);
            one(artifactsToModuleDescriptorConverterMock).addArtifacts(expectedModuleDescriptor, artifactContainerMock, TEST_PUBLISH_SPEC);
            one(dependenciesToModuleDescriptorConverterMock).addDependencyDescriptors(expectedModuleDescriptor, dependencyContainerInternalMock,
                    TEST_DEPENDENCY_SPEC);
        }});

        if (transformer != null) {
            defaultModuleDescriptorConverter.addIvyTransformer(transformer);
        }
        DefaultModuleDescriptor actualModuleDescriptor = (DefaultModuleDescriptor) defaultModuleDescriptorConverter.convert(
                testTransitiveOverride, configurationContainerMock, TEST_CONFIGURATION_SPEC,
                dependencyContainerInternalMock, TEST_DEPENDENCY_SPEC,
                artifactContainerMock, TEST_PUBLISH_SPEC);
        assertThat(actualModuleDescriptor, Matchers.equalTo(expectedModuleDescriptor));
        return actualModuleDescriptor;
    }
}
