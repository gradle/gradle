/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class ExternalModuleDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    ExternalModuleDependencyDescriptorFactory externalModuleDependencyDescriptorFactory =
            new ExternalModuleDependencyDescriptorFactory(excludeRuleConverterStub);
    
    @Test
    public void canConvert() {
        assertThat(externalModuleDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), Matchers.equalTo(false));
        assertThat(externalModuleDependencyDescriptorFactory.canConvert(context.mock(ExternalModuleDependency.class)), Matchers.equalTo(true));
    }

    @Test
    public void testAddWithNullGroupAndNullVersionShouldHaveEmptyStringModuleRevisionValues() {
        ModuleDependency dependency = new DefaultExternalModuleDependency(null, "gradle-core", null, TEST_DEP_CONF);
        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.getDependencyRevisionId(), equalTo(IvyUtil.createModuleRevisionId(dependency)));
    }

    @Test
    public void testCreateFromModuleDependency() {
        DefaultExternalModuleDependency moduleDependency = new DefaultExternalModuleDependency("org.gradle",
                "gradle-core", "1.0", TEST_DEP_CONF);
        setUpDependency(moduleDependency);

        externalModuleDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor,
                moduleDependency);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor
                .getDependencies()[0];

        assertEquals(moduleDependency.isChanging(), dependencyDescriptor.isChanging());
        assertEquals(dependencyDescriptor.isForce(), moduleDependency.isForce());
        assertEquals(IvyUtil.createModuleRevisionId(moduleDependency), dependencyDescriptor.getDependencyRevisionId());
        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor() {
        ModuleDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        ModuleDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);

        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, externalModuleDependencyDescriptorFactory
        );
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndSameClassifiersShouldBePartOfSameDependencyDescriptor() {
        ExternalDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency1, null, "jdk14");

        ExternalDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency2, null, "jdk14");

        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, externalModuleDependencyDescriptorFactory
        );
    }


    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndDifferentClassifiersShouldNotBePartOfSameDependencyDescriptor() {
        ExternalDependency dependency1 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency1, null, "jdk14");

        ExternalDependency dependency2 = new DefaultExternalModuleDependency("org.gradle", "gradle-core", "1.0", TEST_OTHER_DEP_CONF);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency2, null, "jdk15");

        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentClassifiersShouldNotBePartOfSameDependencyDescriptor(
                dependency1, dependency2, externalModuleDependencyDescriptorFactory
        );
    }

    private void assertThataddDependenciesWithSameModuleRevisionIdAndDifferentClassifiersShouldNotBePartOfSameDependencyDescriptor(
            ModuleDependency dependency1, ModuleDependency dependency2, DependencyDescriptorFactoryInternal factoryInternal
    ) {
        factoryInternal.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency1);
        factoryInternal.addDependencyDescriptor(TEST_CONF, moduleDescriptor, dependency2);
        assertThat(moduleDescriptor.getDependencies().length, equalTo(2));

        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF), Matchers.hasItemInArray(TEST_DEP_CONF));
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF).length, equalTo(1));

        dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[1];
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF), Matchers.hasItemInArray(TEST_OTHER_DEP_CONF));
        assertThat(dependencyDescriptor.getDependencyConfigurations(TEST_CONF).length, equalTo(1));
    }

}
