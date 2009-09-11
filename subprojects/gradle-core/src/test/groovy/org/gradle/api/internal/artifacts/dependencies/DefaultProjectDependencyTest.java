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
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.dsl.ConfigurationHandler;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import static org.gradle.util.Matchers.*;
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultProjectDependencyTest extends AbstractModuleDependencyTest {
    private final ProjectDependenciesBuildInstruction instruction = new ProjectDependenciesBuildInstruction(WrapUtil.<String>toList());
    private final Project targetProjectStub = context.mock(Project.class);
    private final ConfigurationHandler targetConfigurationHandlerStub = context.mock(ConfigurationHandler.class);
    private final Configuration targetConfigurationMock = context.mock(Configuration.class);
    private final TaskContainer targetTaskContainerStub = context.mock(TaskContainer.class);
    private final DefaultProjectDependency projectDependency = new DefaultProjectDependency(targetProjectStub, instruction);

    protected AbstractModuleDependency getDependency() {
        return projectDependency;
    }

    protected AbstractModuleDependency createDependency(String group, String name, String version) {
        return createDependency(group, name, version, null);    
    }

    protected AbstractModuleDependency createDependency(String group, String name, String version, String configuration) {
        DefaultProject dependencyProject = HelperUtil.createRootProject(new File(name));
        dependencyProject.setGroup(group);
        dependencyProject.setVersion(version);
        DefaultProjectDependency projectDependency;
        if (configuration != null) {
            projectDependency = new DefaultProjectDependency(dependencyProject, configuration, instruction);
        } else {
            projectDependency = new DefaultProjectDependency(dependencyProject, instruction);
        }
        return projectDependency;
    }

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(targetProjectStub).getConfigurations();
            will(returnValue(targetConfigurationHandlerStub));
            allowing(targetConfigurationHandlerStub).getByName("default");
            will(returnValue(targetConfigurationMock));
            allowing(targetProjectStub).getTasks();
            will(returnValue(targetTaskContainerStub));
            allowing(targetProjectStub).getName();
            will(returnValue("target-name"));
            allowing(targetProjectStub).getGroup();
            will(returnValue("target-group"));
            allowing(targetProjectStub).getVersion();
            will(returnValue("target-version"));
            allowing(targetConfigurationMock).getUploadInternalTaskName();
            will(returnValue("target-upload"));
        }});
    }

    @Test
    public void init() {
        assertTrue(projectDependency.isTransitive());
        assertEquals("target-name", projectDependency.getName());
        assertEquals("target-group", projectDependency.getGroup());
        assertEquals("target-version", projectDependency.getVersion());
    }

    @Test
    public void getConfiguration() {
        context.checking(new Expectations() {{
            allowing(targetConfigurationHandlerStub).getByName("conf1");
            will(returnValue(targetConfigurationMock));
        }});

        DefaultProjectDependency projectDependency = new DefaultProjectDependency(targetProjectStub, "conf1", instruction);
        assertThat(projectDependency.getProjectConfiguration(), sameInstance(targetConfigurationMock));
    }

    @Test
    public void resolveDelegatesToAllSelfResolvingDependenciesInTargetConfiguration() {
        final SelfResolvingDependency selfResolvingDependency = context.mock(SelfResolvingDependency.class);
        final Set<File> selfResolvingFiles = toSet(new File("somePath"));
        context.checking(new Expectations() {{
            allowing(targetConfigurationHandlerStub).getByName("conf1");
            will(returnValue(targetConfigurationMock));

            allowing(targetConfigurationMock).getAllDependencies(SelfResolvingDependency.class);
            will(returnValue(toSet(selfResolvingDependency)));

            allowing(selfResolvingDependency).resolve();
            will(returnValue(selfResolvingFiles));
        }});
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(targetProjectStub, "conf1", instruction);
        assertThat(projectDependency.resolve(), equalTo(selfResolvingFiles));
    }

    private Task taskInTargetProject(final String name) {
        final Task task = context.mock(Task.class, name);
        context.checking(new Expectations(){{
            allowing(targetTaskContainerStub).getByName(name);
            will(returnValue(task));
        }});
        return task;
    }

    @Test
    public void dependsOnTargetConfigurationAndUploadInternalTaskOfTargetConfiguration() {
        Task uploadTask = taskInTargetProject("target-upload");
        Task a = taskInTargetProject("a");
        Task b = taskInTargetProject("b");
        expectTargetConfigurationHasDependencies(a, b);

        assertThat(projectDependency.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(uploadTask, a, b)));
    }

    private void expectTargetConfigurationHasDependencies(final Task... tasks) {
        context.checking(new Expectations(){{
            TaskDependency dependencyStub = context.mock(TaskDependency.class);

            allowing(targetConfigurationMock).getBuildDependencies();
            will(returnValue(dependencyStub));

            allowing(dependencyStub).getDependencies(null);
            will(returnValue(toSet(tasks)));
        }});
    }

    private void expectTargetConfigurationHasNoDependencies() {
        context.checking(new Expectations(){{
            TaskDependency dependencyStub = context.mock(TaskDependency.class);

            allowing(targetConfigurationMock).getBuildDependencies();
            will(returnValue(dependencyStub));

            allowing(dependencyStub).getDependencies(null);
            will(returnValue(toSet()));
        }});
    }

    @Test
    public void doesNotDependOnAnythingWhenProjectRebuildIsDisabled() {
        DefaultProjectDependency dependency = new DefaultProjectDependency(targetProjectStub,
                new ProjectDependenciesBuildInstruction(null));
        assertThat(dependency.getBuildDependencies().getDependencies(null), isEmpty());
    }

    @Test
    public void dependsOnAdditionalTasksFromTargetProject() {
        expectTargetConfigurationHasNoDependencies();

        Task uploadTask = taskInTargetProject("target-upload");
        Task a = taskInTargetProject("a");
        Task b = taskInTargetProject("b");

        DefaultProjectDependency dependency = new DefaultProjectDependency(targetProjectStub,
                new ProjectDependenciesBuildInstruction(toList("a", "b")));
        assertThat(dependency.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(uploadTask, a, b)));
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
        ProjectDependency copiedDependency = dependency.copy();
        assertDeepCopy(dependency, copiedDependency);
        assertThat(copiedDependency.getDependencyProject(), sameInstance(dependency.getDependencyProject()));
    }

    private ProjectDependency createProjectDependency() {
        ProjectDependency projectDependency = new DefaultProjectDependency(HelperUtil.createRootProject(), "conf", instruction);
        projectDependency.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"));
        return projectDependency;
    }

    @Test
    @Override
    public void equality() {
        assertThat(new DefaultProjectDependency(targetProjectStub, instruction), strictlyEqual(new DefaultProjectDependency(
                targetProjectStub, instruction)));
        assertThat(new DefaultProjectDependency(targetProjectStub, "conf1", instruction), strictlyEqual(new DefaultProjectDependency(
                targetProjectStub, "conf1", instruction)));
        assertThat(new DefaultProjectDependency(targetProjectStub, "conf1", instruction), not(equalTo(new DefaultProjectDependency(
                targetProjectStub, "conf2", instruction))));
        Project otherProject = context.mock(Project.class, "otherProject");
        assertThat(new DefaultProjectDependency(targetProjectStub, instruction), not(equalTo(new DefaultProjectDependency(
                otherProject, instruction))));
        assertThat(new DefaultProjectDependency(targetProjectStub, instruction), not(equalTo(new DefaultProjectDependency(
                targetProjectStub, new ProjectDependenciesBuildInstruction(null)))));
    }
}
