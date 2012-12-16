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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.Matchers.strictlyEqual;
import static org.gradle.util.WrapUtil.toList;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultProjectDependencyTest extends AbstractModuleDependencyTest {
    private final ProjectDependenciesBuildInstruction instruction = new ProjectDependenciesBuildInstruction(true);
    private final ProjectInternal dependencyProjectStub = context.mock(ProjectInternal.class);
    private final ConfigurationContainerInternal projectConfigurationsStub = context.mock(ConfigurationContainerInternal.class);
    private final ConfigurationInternal projectConfigurationStub = context.mock(ConfigurationInternal.class);
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
        assertThat(projectDependency.getProjectConfiguration(), sameInstance((Configuration) projectConfigurationStub));
    }

    @Test
    public void resolveDelegatesToAllSelfResolvingDependenciesInTargetConfiguration() {
        final DependencyResolveContext resolveContext = context.mock(DependencyResolveContext.class);
        final Dependency projectSelfResolvingDependency = context.mock(Dependency.class);
        final ProjectDependency transitiveProjectDependencyStub = context.mock(ProjectDependency.class);
        final DependencySet dependencies = context.mock(DependencySet.class);
        context.checking(new Expectations() {{
            allowing(projectConfigurationsStub).getByName("conf1");
            will(returnValue(projectConfigurationStub));

            allowing(projectConfigurationStub).getAllDependencies();
            will(returnValue(dependencies));

            allowing(dependencies).iterator();
            will(returnIterator(toList(projectSelfResolvingDependency, transitiveProjectDependencyStub)));

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
        context.checking(new Expectations() {{
            allowing(resolveContext).isTransitive();
            will(returnValue(false));
        }});
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf1",
                instruction);
        projectDependency.resolve(resolveContext);
    }
    
    @Test
    public void resolveNotDelegatesToTransitiveProjectDependenciesIfProjectDependencyIsNonTransitive() {
        DependencyResolveContext resolveContext = context.mock(DependencyResolveContext.class);
        DefaultProjectDependency projectDependency = new DefaultProjectDependency(dependencyProjectStub, "conf1", instruction);
        projectDependency.setTransitive(false);
        projectDependency.resolve(resolveContext);
    }

    private Task taskInTargetProject(final String name) {
        final Task task = context.mock(Task.class, name);
        context.checking(new Expectations(){{
            allowing(dependencyProjectStub).evaluate();
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
            PublishArtifactSet artifacts = context.mock(PublishArtifactSet.class);
            TaskDependency dependencyStub = context.mock(TaskDependency.class, "artifacts");

            allowing(projectConfigurationStub).getAllArtifacts();
            will(returnValue(artifacts));

            allowing(artifacts).getBuildDependencies();
            will(returnValue(dependencyStub));

            allowing(dependencyStub).getDependencies(null);
            will(returnValue(toSet(tasks)));
        }});
    }

    @Test
    public void doesNotDependOnAnythingWhenProjectRebuildIsDisabled() {
        DefaultProjectDependency dependency = new DefaultProjectDependency(dependencyProjectStub,
                new ProjectDependenciesBuildInstruction(false));
        assertThat(dependency.getBuildDependencies().getDependencies(null), isEmpty());
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
        ProjectInternal otherProject = context.mock(ProjectInternal.class, "otherProject");
        assertThat(new DefaultProjectDependency(dependencyProjectStub, instruction), not(equalTo(new DefaultProjectDependency(
                otherProject, instruction))));
        assertThat(new DefaultProjectDependency(dependencyProjectStub, instruction), not(equalTo(new DefaultProjectDependency(
                dependencyProjectStub, new ProjectDependenciesBuildInstruction(false)))));
    }
}
