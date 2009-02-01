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
package org.gradle.api.internal.dependencies.ivyservice;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.dependencies.Dependency;
import org.gradle.api.dependencies.DependencyContainer;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Hans Dockter
 */
public class DefaultClientModuleDescriptorFactoryTest {
    private static final ModuleRevisionId TEST_MODULE_REVISION_ID = ModuleRevisionId.newInstance("org", "name", "version");
    private DefaultClientModuleDescriptorFactory clientModuleDescriptorFactory;
    private DependencyContainer dependencyContainerMock;

    private Dependency dependencyMock;
    private JUnit4Mockery context = new JUnit4Mockery();
    private DependencyDescriptor testDependencyDescriptor;

    @Before
    public void setUp() {
        testDependencyDescriptor = context.mock(DependencyDescriptor.class);
        clientModuleDescriptorFactory = new DefaultClientModuleDescriptorFactory();
        dependencyContainerMock = context.mock(DependencyContainer.class);
        dependencyMock = context.mock(Dependency.class);
        context.checking(new Expectations() {{
            allowing(dependencyMock).createDependencyDescriptor(with(any(ModuleDescriptor.class)));
            will(returnValue(testDependencyDescriptor));

            allowing(dependencyContainerMock).getDependencies();
            will(returnValue(WrapUtil.toList(dependencyMock)));
        }});
    }

    @Test
    public void testCreateModuleDescriptor() {
        ModuleDescriptor moduleDescriptor = clientModuleDescriptorFactory.createModuleDescriptor(
                TEST_MODULE_REVISION_ID, dependencyContainerMock);
        assertThat(moduleDescriptor.getModuleRevisionId(), equalTo(TEST_MODULE_REVISION_ID));
        assertThat(Arrays.asList(moduleDescriptor.getDependencies()), equalTo(WrapUtil.toList(testDependencyDescriptor)));
    }
}
