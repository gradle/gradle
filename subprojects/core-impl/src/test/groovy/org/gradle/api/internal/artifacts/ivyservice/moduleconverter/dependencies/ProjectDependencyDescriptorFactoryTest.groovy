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


import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.project.AbstractProject
import org.gradle.initialization.ProjectAccessListener
import org.gradle.util.HelperUtil
import org.jmock.integration.junit4.JUnit4Mockery
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
//TODO SF spockify
public class ProjectDependencyDescriptorFactoryTest extends AbstractDependencyDescriptorFactoryInternalTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private ProjectIvyDependencyDescriptorFactory projectDependencyDescriptorFactory =
            new ProjectIvyDependencyDescriptorFactory(excludeRuleConverterStub);

    @Test
    public void canConvert() {
        assertThat(projectDependencyDescriptorFactory.canConvert(context.mock(ProjectDependency.class)), equalTo(true));
        assertThat(projectDependencyDescriptorFactory.canConvert(context.mock(ExternalModuleDependency.class)), equalTo(false));
    }

    @Test
    public void testCreateFromProjectDependency() {
        ProjectDependency projectDependency = createProjectDependency(TEST_DEP_CONF);
        setUpDependency(projectDependency);
        ProjectDependencyDescriptor dependencyDescriptor = (ProjectDependencyDescriptor) projectDependencyDescriptorFactory.createDependencyDescriptor(TEST_CONF, projectDependency, moduleDescriptor);

        assertDependencyDescriptorHasCommonFixtureValues(dependencyDescriptor);
        assertFalse(dependencyDescriptor.isChanging());
        assertFalse(dependencyDescriptor.isForce());
        assertEquals(ModuleRevisionId.newInstance("someGroup", "test", "someVersion"), dependencyDescriptor.getDependencyRevisionId());
        assertSame(projectDependency.getDependencyProject(), dependencyDescriptor.getTargetProject());
    }

    private ProjectDependency createProjectDependency(String dependencyConfiguration) {
        AbstractProject dependencyProject = HelperUtil.createRootProject();
        dependencyProject.setGroup("someGroup");
        dependencyProject.setVersion("someVersion");
        return new DefaultProjectDependency(dependencyProject, dependencyConfiguration, {} as ProjectAccessListener, true);
    }
}