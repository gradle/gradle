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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.ConfigurationHandler;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.not;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultProjectDependencyTest extends AbstractDependencyTest {
    private DefaultProjectDependency projectDependency;
    private Project dependencyProject;

    protected AbstractDependency getDependency() {
        return projectDependency;
    }

    protected AbstractDependency createDependency(String group, String name, String version) {
        return createDependency(group, name, version, null);    
    }

    protected AbstractDependency createDependency(String group, String name, String version, String dependencyConfiguration) {
        DefaultProject dependencyProject = HelperUtil.createRootProject(new File(name));
        dependencyProject.setGroup(group);
        dependencyProject.setVersion(version);
        DefaultProjectDependency projectDependency;
        if (dependencyConfiguration != null) {
            projectDependency = new DefaultProjectDependency(dependencyProject, dependencyConfiguration);
        } else {
            projectDependency = new DefaultProjectDependency(dependencyProject); 
        }
        return projectDependency;
    }

    @Before public void setUp() {
        dependencyProject = HelperUtil.createRootProject(new File("dependency"));
        projectDependency = new DefaultProjectDependency(dependencyProject);
    }
    
    @Test
    public void init() {
        projectDependency = new DefaultProjectDependency(dependencyProject);
        assertTrue(projectDependency.isTransitive());
        assertEquals(dependencyProject.getName(), projectDependency.getName());
        assertEquals(dependencyProject.getGroup().toString(), projectDependency.getGroup());
        assertEquals(dependencyProject.getVersion().toString(), projectDependency.getVersion());
    }

    @Test
    public void getConfiguration() {
        final Configuration configurationDummy = context.mock(Configuration.class);
        final ConfigurationHandler configurationHandlerStub = context.mock(ConfigurationHandler.class);
        final Project projectStub = context.mock(Project.class);
        context.checking(new Expectations() {{
            allowing(projectStub).getConfigurations();
            will(returnValue(configurationHandlerStub));

            allowing(configurationHandlerStub).getByName("conf1");
            will(returnValue(configurationDummy));
        }});
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(projectStub, "conf1");
        assertThat(projectDependency.getConfiguration(), sameInstance(configurationDummy));
    }

    @Test
    public void contentEqualsWithEqualDependencies() {
        ProjectDependency dependency1 = createProjectDependency();
        ProjectDependency dependency2 = createProjectDependency();
        assertThat(dependency1.contentEquals(dependency2), equalTo(true));
    }

    @Test
    public void contentEqualsWithNonEqualDependencies() {
        ProjectDependency dependency1 = createProjectDependency();
        ProjectDependency dependency2 = createProjectDependency();
        dependency2.setTransitive(!dependency1.isTransitive());
        assertThat(dependency1.contentEquals(dependency2), equalTo(false));
    }

    @Test
    public void copy() {
        ProjectDependency dependency = createProjectDependency();
        ProjectDependency copiedDependency = (ProjectDependency) dependency.copy();
        assertDeepCopy(dependency, copiedDependency);
        assertThat(copiedDependency.getDependencyProject(), sameInstance(dependency.getDependencyProject()));
    }

    private ProjectDependency createProjectDependency() {
        ProjectDependency projectDependency = new DefaultProjectDependency(HelperUtil.createRootProject());
        projectDependency.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"));
        return projectDependency;
    }

    @Test
    @Override
    public void equality() {
        assertThat(new DefaultProjectDependency(dependencyProject), equalTo(new DefaultProjectDependency(dependencyProject)));
        assertThat(new DefaultProjectDependency(dependencyProject).hashCode(), equalTo(new DefaultProjectDependency(dependencyProject).hashCode()));
        assertThat(new DefaultProjectDependency(dependencyProject, "conf1"), equalTo(new DefaultProjectDependency(dependencyProject, "conf1")));
        assertThat(new DefaultProjectDependency(dependencyProject, "conf1").hashCode(), equalTo(new DefaultProjectDependency(dependencyProject, "conf1").hashCode()));
        assertThat(new DefaultProjectDependency(dependencyProject, "conf1"), not(equalTo(new DefaultProjectDependency(dependencyProject, "conf2"))));
        assertThat(new DefaultProjectDependency(dependencyProject), not(equalTo(new DefaultProjectDependency(context.mock(Project.class, "otherProject")))));
    }


}
