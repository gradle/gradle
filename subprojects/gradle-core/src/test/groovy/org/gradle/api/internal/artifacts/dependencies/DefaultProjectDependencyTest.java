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
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultProjectDependencyTest extends AbstractModuleDependencyTest {
    private final ProjectDependenciesBuildInstruction instruction = new ProjectDependenciesBuildInstruction(WrapUtil.<String>toList());
    private final Project dependencyProjectStub = context.mock(Project.class);
    private final ConfigurationContainer projectConfigurationsStub = context.mock(ConfigurationContainer.class);
    private final Configuration projectConfigurationStub = context.mock(Configuration.class);
    private final TaskContainer dependencyProjectTaskContainerStub = context.mock(TaskContainer.class);
    private final DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, instruction);

    protected AbstractModuleDependency getDependency() {
        return projectDependency;
    }

    protected AbstractModuleDependency createDependency(String group, String name, String version) {
        return createDependency(group, name, version, null);    
    }

    protected AbstractModuleDependency createDependency(String group, String name, String version, String configuration) {
        ProjectInternal dependencyProject = context.mock(ProjectInternal.class);
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
            allowing(dependencyProjectStub).getConfigurations();
            will(returnValue(projectConfigurationsStub));
            allowing(projectConfigurationsStub).getByName("default");
            will(returnValue(projectConfigurationStub));
            allowing(dependencyProjectStub).getTasks();
            will(returnValue(dependencyProjectTaskContainerStub));
            allowing(dependencyProjectStub).getName();
            will(returnValue("target-name"));
            allowing(dependencyProjectStub).getGroup();
            will(returnValue("target-group"));
            allowing(dependencyProjectStub).getVersion();
            will(returnValue("target-version"));
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
            allowing(projectConfigurationsStub).getByName("conf1");
            will(returnValue(projectConfigurationStub));
        }});

        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf1", instruction);
        assertThat(projectDependency.getProjectConfiguration(), sameInstance(projectConfigurationStub));
    }

    @Test
    public void resolveDelegatesToAllSelfResolvingDependenciesInTargetConfiguration() {
        final DependencyResolveContext resolveContext = context.mock(DependencyResolveContext.class);
        final Dependency projectSelfResolvingDependency = context.mock(Dependency.class);
        final ProjectDependency transitiveProjectDependencyStub = context.mock(ProjectDependency.class);
        context.checking(new Expectations() {{
            allowing(projectConfigurationsStub).getByName("conf1");
            will(returnValue(projectConfigurationStub));

            allowing(projectConfigurationStub).getAllDependencies();
            will(returnValue(toSet(projectSelfResolvingDependency, transitiveProjectDependencyStub)));

            allowing(resolveContext).isTransitive();
            will(returnValue(true));
            
            one(resolveContext).add(projectSelfResolvingDependency);
            
            one(resolveContext).add(transitiveProjectDependencyStub);
        }});

        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf1",
                instruction);
        projectDependency.resolve(resolveContext);
    }

    @Test
    public void resolveNotDelegatesToProjectDependenciesInTargetConfigurationIfConfigurationIsNonTransitive() {
        final DependencyResolveContext resolveContext = context.mock(DependencyResolveContext.class);
        final Dependency projectSelfResolvingDependency = context.mock(Dependency.class);
        final ProjectDependency transitiveProjectDependencyStub = context.mock(ProjectDependency.class);
        context.checking(new Expectations() {{
            allowing(projectConfigurationsStub).getByName("conf1");
            will(returnValue(projectConfigurationStub));

            allowing(projectConfigurationStub).getAllDependencies();
            will(returnValue(toSet(projectSelfResolvingDependency, transitiveProjectDependencyStub)));

            allowing(resolveContext).isTransitive();
            will(returnValue(false));

            one(resolveContext).add(projectSelfResolvingDependency);
        }});
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf1",
                instruction);
        projectDependency.resolve(resolveContext);
    }
    
    @Test
    public void resolveNotDelegatesToTransitiveProjectDependenciesIfProjectDependencyIsNonTransitive() {
        final DependencyResolveContext resolveContext = context.mock(DependencyResolveContext.class);
        final SelfResolvingDependency projectSelfResolvingDependency = context.mock(SelfResolvingDependency.class);
        final ProjectDependency transitiveProjectDependencyStub = context.mock(ProjectDependency.class);
        context.checking(new Expectations() {{
            allowing(projectConfigurationsStub).getByName("conf1");
            will(returnValue(projectConfigurationStub));

            allowing(projectConfigurationStub).getAllDependencies();
            will(returnValue(toSet(projectSelfResolvingDependency, transitiveProjectDependencyStub)));

            one(resolveContext).add(projectSelfResolvingDependency);
        }});
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf1", instruction);
        projectDependency.setTransitive(false);
        projectDependency.resolve(resolveContext);
    }

    private Task taskInTargetProject(final String name) {
        final Task task = context.mock(Task.class, name);
        context.checking(new Expectations(){{
            allowing(dependencyProjectTaskContainerStub).getByName(name);
            will(returnValue(task));
        }});
        return task;
    }

    @Test
    public void dependsOnTargetConfigurationAndArtifactsOfTargetConfiguration() {
        Task a = taskInTargetProject("a");
        Task b = taskInTargetProject("b");
        Task c = taskInTargetProject("c");
        expectTargetConfigurationHasDependencies(a, b);
        expectTargetConfigurationHasArtifacts(c);

        assertThat(projectDependency.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(a, b, c)));
    }

    private void expectTargetConfigurationHasDependencies(final Task... tasks) {
        context.checking(new Expectations(){{
            TaskDependency dependencyStub = context.mock(TaskDependency.class, "dependencies");

            allowing(projectConfigurationStub).getBuildDependencies();
            will(returnValue(dependencyStub));

            allowing(dependencyStub).getDependencies(null);
            will(returnValue(toSet(tasks)));
        }});
    }

    private void expectTargetConfigurationHasArtifacts(final Task... tasks) {
        context.checking(new Expectations(){{
            TaskDependency dependencyStub = context.mock(TaskDependency.class, "artifacts");

            allowing(projectConfigurationStub).getBuildArtifacts();
            will(returnValue(dependencyStub));

            allowing(dependencyStub).getDependencies(null);
            will(returnValue(toSet(tasks)));
        }});
    }

    private void expectTargetConfigurationHasNoDependencies() {
        context.checking(new Expectations(){{
            TaskDependency dependencyStub = context.mock(TaskDependency.class);

            allowing(projectConfigurationStub).getBuildDependencies();
            will(returnValue(dependencyStub));

            allowing(projectConfigurationStub).getBuildArtifacts();
            will(returnValue(dependencyStub));

            allowing(dependencyStub).getDependencies(null);
            will(returnValue(toSet()));
        }});
    }

    @Test
    public void doesNotDependOnAnythingWhenProjectRebuildIsDisabled() {
        DefaultProjectDependency dependency = new DefaultProjectDependency(dependencyProjectStub,
                new ProjectDependenciesBuildInstruction(null));
        assertThat(dependency.getBuildDependencies().getDependencies(null), isEmpty());
    }

    @Test
    public void dependsOnAdditionalTasksFromTargetProject() {
        expectTargetConfigurationHasNoDependencies();

        Task a = taskInTargetProject("a");
        Task b = taskInTargetProject("b");

        DefaultProjectDependency dependency = new DefaultProjectDependency(dependencyProjectStub,
                new ProjectDependenciesBuildInstruction(toList("a", "b")));
        assertThat(dependency.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(a, b)));
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
        ProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf", instruction);
        projectDependency.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"));
        return projectDependency;
    }

    @Test
    @Override
    public void equality() {
        assertThat(new DefaultProjectDependency(dependencyProjectStub, instruction), strictlyEqual(new DefaultProjectDependency(
                dependencyProjectStub, instruction)));
        assertThat(new DefaultProjectDependency(dependencyProjectStub, "conf1", instruction), strictlyEqual(new DefaultProjectDependency(
                dependencyProjectStub, "conf1", instruction)));
        assertThat(new DefaultProjectDependency(dependencyProjectStub, "conf1", instruction), not(equalTo(new DefaultProjectDependency(
                dependencyProjectStub, "conf2", instruction))));
        Project otherProject = context.mock(Project.class, "otherProject");
        assertThat(new DefaultProjectDependency(dependencyProjectStub, instruction), not(equalTo(new DefaultProjectDependency(
                otherProject, instruction))));
        assertThat(new DefaultProjectDependency(dependencyProjectStub, instruction), not(equalTo(new DefaultProjectDependency(
                dependencyProjectStub, new ProjectDependenciesBuildInstruction(null)))));
    }
}
