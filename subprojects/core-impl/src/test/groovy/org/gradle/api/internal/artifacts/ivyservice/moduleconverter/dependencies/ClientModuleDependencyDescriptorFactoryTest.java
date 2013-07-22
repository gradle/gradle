/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

public class ClientModuleDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private ModuleDescriptorFactoryForClientModule moduleDescriptorFactoryForClientModule = context.mock(ModuleDescriptorFactoryForClientModule.class);
    private ClientModuleIvyDependencyDescriptorFactory clientModuleDependencyDescriptorFactory = new ClientModuleIvyDependencyDescriptorFactory(
            excludeRuleConverterStub,
            moduleDescriptorFactoryForClientModule
    );

    @Test
    public void canConvert() {
        assertThat(clientModuleDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), Matchers.equalTo(false));
        assertThat(clientModuleDependencyDescriptorFactory.canConvert(context.mock(ClientModule.class)), Matchers.equalTo(true));
    }

    @Test
    public void testAddDependencyDescriptorForClientModule() {
        final ModuleDependency dependencyDependency = context.mock(ModuleDependency.class, "dependencyDependency");
        final DefaultClientModule clientModule = new DefaultClientModule("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        final ModuleRevisionId testModuleRevisionId = IvyUtil.createModuleRevisionId(clientModule);

        setUpDependency(clientModule);
        clientModule.addDependency(dependencyDependency);
        final ModuleDescriptor moduleDescriptorForClientModule = context.mock(ModuleDescriptor.class);
        context.checking(new Expectations() {{
            allowing(moduleDescriptorFactoryForClientModule).createModuleDescriptor(
                    testModuleRevisionId,
                    WrapUtil.toSet(dependencyDependency)
            );
            will(returnValue(moduleDescriptorForClientModule));
        }});

        DefaultDependencyDescriptor dependencyDescriptor = clientModuleDependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, clientModule, moduleDescriptor);
        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
        assertEquals(testModuleRevisionId, dependencyDescriptor.getDependencyRevisionId());
        assertFalse(dependencyDescriptor.isChanging());
    }

    @Test
    public void testAddWithNullGroupAndNullVersionShouldHaveEmptyStringModuleRevisionValues() {
        final ClientModule clientModule = new DefaultClientModule(null, "gradle-core", null, TEST_DEP_CONF);
        final ModuleRevisionId testModuleRevisionId = IvyUtil.createModuleRevisionId(clientModule);
        final ModuleDescriptor moduleDescriptorForClientModule = context.mock(ModuleDescriptor.class);
        context.checking(new Expectations() {{
            allowing(moduleDescriptorFactoryForClientModule).createModuleDescriptor(
                    testModuleRevisionId,
                    WrapUtil.<ModuleDependency>toSet()
            );
            will(returnValue(moduleDescriptorForClientModule));
        }});

        DefaultDependencyDescriptor dependencyDescriptor = clientModuleDependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, clientModule, moduleDescriptor);
        assertThat(dependencyDescriptor.getDependencyRevisionId(), equalTo(testModuleRevisionId));
    }
}