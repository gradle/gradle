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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private ProjectDependencyModuleRevisionIdStrategy moduleRevisionIdStrategyStub = context.mock(ProjectDependencyModuleRevisionIdStrategy.class);
    private ProjectDependencyDescriptorFactory projectDependencyDescriptorFactory =
            new ProjectDependencyDescriptorFactory(excludeRuleConverterStub, moduleRevisionIdStrategyStub);

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
            allowing(moduleRevisionIdStrategyStub).createModuleRevisionId(projectDependency);
            will(returnValue(someModuleRevisionId));
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
        AbstractProject dependencyProject = HelperUtil.createRootProject(new File("someName"));
        dependencyProject.setGroup("someGroup");
        dependencyProject.setVersion("someVersion");
        final ProjectDependency projectDependency = new DefaultProjectDependency(dependencyProject, dependencyConfiguration,
                new ProjectDependenciesBuildInstruction(Collections.<String>emptyList()));
        return projectDependency;
    }

    @Test
    public void addExternalModuleDependenciesWithSameModuleRevisionIdAndDifferentConfs_shouldBePartOfSameDependencyDescriptor() {
        final ProjectDependency dependency1 = createProjectDependency(TEST_DEP_CONF);
        final ProjectDependency dependency2 = createProjectDependency(TEST_OTHER_DEP_CONF);

        context.checking(new Expectations() {{
            allowing(moduleRevisionIdStrategyStub).createModuleRevisionId(dependency1);
            will(returnValue(IvyUtil.createModuleRevisionId(dependency1)));
            allowing(moduleRevisionIdStrategyStub).createModuleRevisionId(dependency2);
            will(returnValue(IvyUtil.createModuleRevisionId(dependency2)));
        }});
        
        assertThataddDependenciesWithSameModuleRevisionIdAndDifferentConfs_shouldBePartOfSameDependencyDescriptor(
                dependency1, dependency2, projectDependencyDescriptorFactory
        );
    }

    @Test
    public void ivyFileModuleRevisionId_shouldBeDeterminedByGroupNameVersionWithoutExtraAttributes() {
        ProjectDependency projectDependency = createProjectDependency(TEST_CONF);
        ModuleRevisionId moduleRevisionId =
                ProjectDependencyDescriptorFactory.IVY_FILE_MODULE_REVISION_ID_STRATEGY.createModuleRevisionId(projectDependency);
        assertThat(moduleRevisionId.getOrganisation(), equalTo(projectDependency.getGroup()));
        assertThat(moduleRevisionId.getName(), equalTo(projectDependency.getName()));
        assertThat(moduleRevisionId.getRevision(), equalTo(projectDependency.getVersion()));
        assertThat(moduleRevisionId.getExtraAttributes(), equalTo((Map) new HashMap()));
    }

    @Test
    public void resolveModuleRevisionId_shouldBeDeterminedByGroupPathVersionPlusExtraAttributes() {
        ProjectDependency projectDependency = createProjectDependency(TEST_CONF);
        ModuleRevisionId moduleRevisionId =
                ProjectDependencyDescriptorFactory.RESOLVE_MODULE_REVISION_ID_STRATEGY.createModuleRevisionId(projectDependency);
        assertThat(moduleRevisionId.getOrganisation(), equalTo(projectDependency.getGroup()));
        assertThat(moduleRevisionId.getName(), equalTo(projectDependency.getDependencyProject().getPath()));
        assertThat(moduleRevisionId.getRevision(), equalTo(projectDependency.getVersion()));
        assertThat(moduleRevisionId.getExtraAttributes(),
                equalTo((Map) WrapUtil.toMap(DependencyDescriptorFactory.PROJECT_PATH_KEY, projectDependency.getDependencyProject().getPath())));
    }


}