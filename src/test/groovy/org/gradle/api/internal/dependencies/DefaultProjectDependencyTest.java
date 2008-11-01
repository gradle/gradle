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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultProjectDependencyTest extends AbstractDependencyTest {
    private static final String TEST_DEPENDENCY_CONF = "depconf";

    private DefaultProjectDependency projectDependency;
    private Project project;
    private Project dependencyProject;
    private ModuleRevisionId dependencyProjectModuleRevisionId;
    private String dependencyProjectArtifactProductionTaskName;
    private DependencyManager mockDependencyManager;
    private DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    protected AbstractDependency getDependency() {
        return projectDependency;
    }

    protected Object getUserDescription() {
        return dependencyProject;
    }

    protected void expectDescriptorBuilt(final DependencyDescriptor descriptor) {
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createFromProjectDependency(getParentModuleDescriptorMock(),
                    projectDependency);
            will(returnValue(descriptor));
        }});
    }

    @Before public void setUp() {
        project = HelperUtil.createRootProject(new File("root"));
        dependencyProjectModuleRevisionId = new ModuleRevisionId(new ModuleId("org", "otherproject"), "1.0");
        dependencyProjectArtifactProductionTaskName = "somename";
        mockDependencyManager = context.mock(DependencyManager.class);
        context.checking(new Expectations() {{
            allowing(mockDependencyManager).createModuleRevisionId(); will(returnValue(dependencyProjectModuleRevisionId));
            allowing(mockDependencyManager).getArtifactProductionTaskName(); will(returnValue(dependencyProjectArtifactProductionTaskName));

        }});
        dependencyProject = HelperUtil.createRootProject(new File("dependency"));
        ((AbstractProject) dependencyProject).setDependencies(mockDependencyManager);
        dependencyProject.createTask(dependencyProjectArtifactProductionTaskName);
        projectDependency = new DefaultProjectDependency(TEST_CONF_MAPPING, dependencyProject, project);
        super.setUp();
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        projectDependency.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
    }
    
    @Test
    public void init() {
        projectDependency = new DefaultProjectDependency(TEST_CONF_MAPPING, dependencyProject, project);
        assertTrue(projectDependency.isTransitive());
        assertNotNull(projectDependency.getExcludeRules());
        assertNotNull(projectDependency.getDependencyConfigurationMappings());
        assertEquals(dependencyProjectModuleRevisionId.getName(), projectDependency.getName());
        assertEquals(dependencyProjectModuleRevisionId.getOrganisation(), projectDependency.getGroup());
        assertEquals(dependencyProjectModuleRevisionId.getRevision(), projectDependency.getVersion());
    }

    @Test (expected = UnknownDependencyNotation.class) public void testWithSingleString() {
        new DefaultProjectDependency(TEST_CONF_MAPPING, "string", project);
    }

    @Test (expected = UnknownDependencyNotation.class) public void testWithUnknownType() {
        new DefaultProjectDependency(TEST_CONF_MAPPING, new Point(3, 4), project);
    }

    @Test public void testInitialize() {
        final Project projectMock = context.mock(Project.class, "project");
        final Task task = context.mock(Task.class);
        final Map<String, Set<String>> tasks4conf = new HashMap<String, Set<String>>();
        final Project dependencyProjectMock = context.mock(ProjectInternal.class, "dependencyProject");
        final DependencyManager dependencyProjectDependencyManager = context.mock(DependencyManager.class, "dependencyProjectDependencyManager");
        final Task expectedArtifactProductionTask = context.mock(Task.class, "artifactProductionTask");
        final String expectedArtifactProductionTaskName = "artifactTask";
        final String expectedArtifactProductionTaskPath = "artifactTaskPath";

        projectDependency = new DefaultProjectDependency(TEST_CONF_MAPPING, dependencyProjectMock, projectMock);

        projectDependency.setUserDependencyDescription(dependencyProjectMock);
        tasks4conf.put(TEST_CONF, WrapUtil.toSet(TEST_CONF));

        context.checking(new Expectations() {{
            allowing(projectMock).getDependencies(); will(returnValue(mockDependencyManager));
            allowing(mockDependencyManager).getTasks4Conf(); will(returnValue(tasks4conf));
            allowing(projectMock).task(TEST_CONF); will(returnValue(task));
            one(task).dependsOn(expectedArtifactProductionTaskPath);
            one((ProjectInternal) dependencyProjectMock).evaluate();
            allowing(dependencyProjectMock).getDependencies(); will(returnValue(dependencyProjectDependencyManager));
            allowing(dependencyProjectDependencyManager).getArtifactProductionTaskName(); will(
                returnValue(expectedArtifactProductionTaskName));
            allowing(dependencyProjectMock).task(expectedArtifactProductionTaskName); will(returnValue(expectedArtifactProductionTask));
            allowing(expectedArtifactProductionTask).getPath(); will(returnValue(expectedArtifactProductionTaskPath));

        }});
        projectDependency.initialize();
    }
}
