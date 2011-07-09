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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private ProjectDependencyDescriptorStrategy descriptorStrategyStub = context.mock(ProjectDependencyDescriptorStrategy.class);
    private ProjectDependencyDescriptorFactory projectDependencyDescriptorFactory =
            new ProjectDependencyDescriptorFactory(excludeRuleConverterStub, descriptorStrategyStub);

    @Test
    public void canConvert() {
        assertThat(projectDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), equalTo(true));
        assertThat(projectDependencyDescriptorFactory.canConvert(context.mock(ExternalModuleDependency.class)), equalTo(false));
    }

    @Test
    public void testCreateFromProjectDependency() {
        final ModuleRevisionId someModuleRevisionId = ModuleRevisionId.newInstance("a", "b", "c");
        final ProjectDependency projectDependency = createProjectDependency(TEST_DEP_CONF);
        setUpDependency(projectDependency);
        context.checking(new Expectations() {{
            allowing(descriptorStrategyStub).createModuleRevisionId(projectDependency);
            will(returnValue(someModuleRevisionId));
            allowing(descriptorStrategyStub).isChanging();
            will(returnValue(true));
        }});
        projectDependencyDescriptorFactory.addDependencyDescriptor(TEST_CONF, moduleDescriptor, projectDependency);
        DefaultDependencyDescriptor dependencyDescriptor = (DefaultDependencyDescriptor) moduleDescriptor.getDependencies()[0];

        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
        assertTrue(dependencyDescriptor.isChanging());
        assertFalse(dependencyDescriptor.isForce());
        assertEquals(someModuleRevisionId,
                dependencyDescriptor.getDependencyRevisionId());
    }

    private ProjectDependency createProjectDependency(String dependencyConfiguration) {
        AbstractProject dependencyProject = HelperUtil.createRootProject();
        dependencyProject.setGroup("someGroup");
        dependencyProject.setVersion("someVersion");
        return new DefaultProjectDependency(dependencyProject, dependencyConfiguration, new ProjectDependenciesBuildInstruction(true));
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor() {
        final ProjectDependency dependency1 = createProjectDependency(TEST_DEP_CONF);
        final ProjectDependency dependency2 = createProjectDependency(TEST_OTHER_DEP_CONF);

        context.checking(new Expectations() {{
            allowing(descriptorStrategyStub).isChanging();
            will(returnValue(true));
            allowing(descriptorStrategyStub).createModuleRevisionId(dependency1);
            will(returnValue(IvyUtil.createModuleRevisionId(dependency1)));
            allowing(descriptorStrategyStub).createModuleRevisionId(dependency2);
            will(returnValue(IvyUtil.createModuleRevisionId(dependency2)));
        }});
        
        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfsShouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, projectDependencyDescriptorFactory
        );
    }

    @Test
    public void ivyFileModuleRevisionIdShouldBeDeterminedByModuleForPublicDescriptorWithoutExtraAttributes() {
        ProjectDependency projectDependency = createProjectDependency(TEST_CONF);
        Module module = ((ProjectInternal) projectDependency.getDependencyProject()).getModule();
        ModuleRevisionId moduleRevisionId =
                ProjectDependencyDescriptorFactory.IVY_FILE_DESCRIPTOR_STRATEGY.createModuleRevisionId(projectDependency);
        assertThat(moduleRevisionId.getOrganisation(), equalTo(module.getGroup()));
        assertThat(moduleRevisionId.getName(), equalTo(module.getName()));
        assertThat(moduleRevisionId.getRevision(), equalTo(module.getVersion()));
        assertThat(moduleRevisionId.getExtraAttributes(), equalTo((Map) new HashMap()));
    }

    @Test
    public void resolveModuleRevisionIdShouldBeDeterminedByModuleForResolvePlusExtraAttributes() {
        ProjectDependency projectDependency = createProjectDependency(TEST_CONF);
        Module module = ((ProjectInternal) projectDependency.getDependencyProject()).getModule();
        ModuleRevisionId moduleRevisionId =
                ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY.createModuleRevisionId(projectDependency);
        assertThat(moduleRevisionId.getOrganisation(), equalTo(module.getGroup()));
        assertThat(moduleRevisionId.getName(), equalTo(module.getName()));
        assertThat(moduleRevisionId.getRevision(), equalTo(module.getVersion()));
        assertThat(moduleRevisionId.getExtraAttributes(),
                equalTo((Map) WrapUtil.toMap(DependencyDescriptorFactory.PROJECT_PATH_KEY, projectDependency.getDependencyProject().getPath())));
    }
}