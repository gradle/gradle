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
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.integration.junit4.JMock;
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
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProject);
        if (dependencyConfiguration != null) {
            projectDependency.setDependencyConfiguration(dependencyConfiguration);
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


}
