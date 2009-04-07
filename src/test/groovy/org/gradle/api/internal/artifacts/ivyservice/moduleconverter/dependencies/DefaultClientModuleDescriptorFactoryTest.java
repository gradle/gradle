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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Dependency;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultClientModuleDescriptorFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test
    public void testCreateModuleDescriptor() {
        DefaultClientModuleDescriptorFactory clientModuleDescriptorFactory =
                new DefaultClientModuleDescriptorFactory();
        Dependency dependencyMock = context.mock(Dependency.class);
        DependencyDescriptor dependencyDescriptorDummy = context.mock(DependencyDescriptor.class);
        final ModuleRevisionId TEST_MODULE_REVISION_ID = ModuleRevisionId.newInstance("org", "name", "version");
        DependencyDescriptorFactorySpy dependencyDescriptorFactorySpy = new DependencyDescriptorFactorySpy(dependencyDescriptorDummy);
        Map clientModuleRegistryDummy = WrapUtil.toMap("key", "value");

        ModuleDescriptor moduleDescriptor = clientModuleDescriptorFactory.createModuleDescriptor(
                TEST_MODULE_REVISION_ID,
                WrapUtil.toSet(dependencyMock),
                dependencyDescriptorFactorySpy,
                clientModuleRegistryDummy);

        assertThat(moduleDescriptor.getModuleRevisionId(), equalTo(TEST_MODULE_REVISION_ID));
        assertThatDescriptorHasOnlyDefaultConfiguration(moduleDescriptor);
        assertThat(Arrays.asList(moduleDescriptor.getDependencies()), equalTo(WrapUtil.toList(dependencyDescriptorDummy)));
        assertCorrectCallToDependencyDescriptorFactory(dependencyDescriptorFactorySpy, Dependency.DEFAULT_CONFIGURATION, moduleDescriptor, dependencyMock, clientModuleRegistryDummy);
    }

    private void assertThatDescriptorHasOnlyDefaultConfiguration(ModuleDescriptor moduleDescriptor) {
        assertThat(moduleDescriptor.getConfigurations().length, equalTo(1));
        assertThat(moduleDescriptor.getConfigurationsNames()[0], equalTo(Dependency.DEFAULT_CONFIGURATION));
    }

    private void assertCorrectCallToDependencyDescriptorFactory(DependencyDescriptorFactorySpy dependencyDescriptorFactorySpy,
                                                                String configuration,
                                                                ModuleDescriptor parent,
                                                                Dependency dependency,
                                                                Map clientModuleRegistry) {
        assertThat(dependencyDescriptorFactorySpy.configuration, equalTo(configuration));
        assertThat(dependencyDescriptorFactorySpy.parent, equalTo(parent));
        assertThat(dependencyDescriptorFactorySpy.dependency, equalTo(dependency));
        assertThat(dependencyDescriptorFactorySpy.clientModuleRegistry, sameInstance(clientModuleRegistry));
    }

    private static class DependencyDescriptorFactorySpy implements DependencyDescriptorFactory {
        DependencyDescriptor dependencyDescriptor;

        String configuration;
        ModuleDescriptor parent;
        Dependency dependency;
        Map clientModuleRegistry;

        private DependencyDescriptorFactorySpy(DependencyDescriptor dependencyDescriptor) {
            this.dependencyDescriptor = dependencyDescriptor;
        }

        public DependencyDescriptor createDependencyDescriptor(String configuration, ModuleDescriptor parent,
                                                               Dependency dependency, Map clientModuleRegistry) {
            this.configuration = configuration;
            this.parent = parent;
            this.dependency = dependency;
            this.clientModuleRegistry = clientModuleRegistry;
            return dependencyDescriptor;
        }
    }
}
