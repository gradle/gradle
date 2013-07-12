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
package org.gradle.api.internal.artifacts.configurations;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.*;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

import static org.gradle.util.Matchers.hasSameItems;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultConfigurationTest {
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private ConfigurationResolver dependencyResolver = context.mock(ConfigurationResolver.class);
    private ConfigurationsProvider configurationContainer;
    private ListenerManager listenerManager = context.mock(ListenerManager.class);
    private DependencyMetaDataProvider metaDataProvider = context.mock(DependencyMetaDataProvider.class);
    private DefaultConfiguration configuration;
    private DependencyResolutionListener dependencyResolutionBroadcast = context.mock(DependencyResolutionListener.class);
    private ListenerBroadcast resolutionListenerBroadcast = context.mock(ListenerBroadcast.class); 

    @Before
    public void setUp() {
        configurationContainer = context.mock(ConfigurationsProvider.class);
        context.checking(new Expectations(){{
            allowing(listenerManager).createAnonymousBroadcaster(DependencyResolutionListener.class);
            will(returnValue(resolutionListenerBroadcast));
            allowing(resolutionListenerBroadcast).getSource();
            will(returnValue(dependencyResolutionBroadcast));
            allowing(dependencyResolutionBroadcast).afterResolve(with(any(ResolvableDependencies.class)));
            allowing(dependencyResolutionBroadcast).beforeResolve(with(any(ResolvableDependencies.class)));
            will(returnValue(null));
        }});
        configuration = createNamedConfiguration("path", "name");
    }

    @Test
    public void defaultValues() {
        assertThat(configuration.getName(), equalTo("name"));
        assertThat(configuration.isVisible(), equalTo(true));
        assertThat(configuration.getExtendsFrom().size(), equalTo(0));
        assertThat(configuration.isTransitive(), equalTo(true));
        assertThat(configuration.getDescription(), nullValue());
        assertThat(configuration.getState(), equalTo(Configuration.State.UNRESOLVED));
        assertThat(configuration.getDisplayName(), equalTo("configuration 'path'"));
    }

    @Test
    public void hasUsefulDisplayName() {
        assertThat(configuration.getDisplayName(), equalTo("configuration 'path'"));
        assertThat(configuration.toString(), equalTo("configuration 'path'"));
        assertThat(configuration.getIncoming().toString(), equalTo("dependencies 'path'"));
    }

    @Test
    public void withPrivateVisibility() {
        configuration.setVisible(false);
        assertFalse(configuration.isVisible());
    }

    @Test
    public void withIntransitive() {
        configuration.setTransitive(false);
        assertFalse(configuration.isTransitive());
    }

    @Test
    public void exclude() {
        Map<String, String> excludeArgs1 = toMap("group", "aGroup");
        Map<String, String> excludeArgs2 = toMap("module", "aModule");
        assertThat(configuration.exclude(excludeArgs1), sameInstance(configuration));
        configuration.exclude(excludeArgs2);
        assertThat(configuration.getExcludeRules(), equalTo(WrapUtil.<ExcludeRule>toSet(
                new DefaultExcludeRule("aGroup", null), new DefaultExcludeRule(null, "aModule"))));
    }

    @Test
    public void setExclude() {
        Set<ExcludeRule> excludeRules = WrapUtil.<ExcludeRule>toSet(new DefaultExcludeRule("groupValue", null));
        configuration.setExcludeRules(excludeRules);
        assertThat(configuration.getExcludeRules(), equalTo(excludeRules));
    }

    @Test
    public void withDescription() {
        configuration.setDescription("description");
        assertThat(configuration.getDescription(), equalTo("description"));
    }

    @Test
    public void extendsOtherConfigurations() {
        Configuration configuration1 = createNamedConfiguration("otherConf1");
        configuration.extendsFrom(configuration1);
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1)));

        Configuration configuration2 = createNamedConfiguration("otherConf2");
        configuration.extendsFrom(configuration2);
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1, configuration2)));
    }

    @Test
    public void setExtendsFrom() {
        Configuration configuration1 = createNamedConfiguration("otherConf1");

        configuration.setExtendsFrom(toSet(configuration1));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1)));

        Configuration configuration2 = createNamedConfiguration("otherConf2");
        configuration.setExtendsFrom(toSet(configuration2));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration2)));
    }

    @Test(expected = InvalidUserDataException.class)
    public void extendsFromWithDirectCycleShouldThrowInvalidUserDataEx() {
        Configuration otherConfiguration = createNamedConfiguration("otherConf");
        otherConfiguration.extendsFrom(configuration);
        configuration.extendsFrom(otherConfiguration);
    }

    @Test(expected = InvalidUserDataException.class)
    public void extendsFromWithIndirectCycleShouldThrowInvalidUserDataEx() {
        Configuration otherConfiguration1 = createNamedConfiguration("otherConf1");
        Configuration otherConfiguration2 = createNamedConfiguration("otherConf2");
        configuration.extendsFrom(otherConfiguration1);
        otherConfiguration1.extendsFrom(otherConfiguration2);
        otherConfiguration2.extendsFrom(configuration);
    }

    @Test(expected = InvalidUserDataException.class)
    public void setExtendsFromWithCycleShouldThrowInvalidUserDataEx() {
        Configuration otherConfiguration = createNamedConfiguration("otherConf");
        otherConfiguration.extendsFrom(configuration);
        configuration.setExtendsFrom(toSet(otherConfiguration));
    }

    @Test
    public void getHierarchy() {
        Configuration root1 = createNamedConfiguration("root1");
        Configuration middle1 = createNamedConfiguration("middle1").extendsFrom(root1);
        Configuration root2 = createNamedConfiguration("root2");
        Configuration middle2 = createNamedConfiguration("middle2").extendsFrom(root1, root2);
        createNamedConfiguration("root3");
        Configuration leaf = createNamedConfiguration("leaf1").extendsFrom(middle1, middle2);
        Set<Configuration> hierarchy = leaf.getHierarchy();
        assertThat(hierarchy.size(), equalTo(5));
        assertThat(hierarchy.iterator().next(), equalTo(leaf));
        assertBothExistsAndOneIsBeforeOther(hierarchy, middle1, root1);
        assertBothExistsAndOneIsBeforeOther(hierarchy, middle2, root2);
    }

    private void assertBothExistsAndOneIsBeforeOther(Set<Configuration> hierarchy, Configuration beforeConf, Configuration afterConf) {
        assertThat(hierarchy, hasItem(beforeConf));
        assertThat(hierarchy, hasItem(afterConf));

        boolean foundBeforeConf = false;
        for (Configuration configuration : hierarchy) {
            if (configuration.equals(beforeConf)) {
                foundBeforeConf = true;
            }
            if (configuration.equals(afterConf)) {
                assertThat(foundBeforeConf, equalTo(true));
            }
        }
    }

    @Test
    public void getAll() {
        final Configuration conf1 = createNamedConfiguration("testConf1");
        final Configuration conf2 = createNamedConfiguration("testConf2");
        context.checking(new Expectations(){{
            one(configurationContainer).getAll();
            will(returnValue(toSet(conf1, conf2)));
        }});
        assertThat(configuration.getAll(), equalTo(toSet(conf1, conf2)));
    }

    @Test(expected = GradleException.class)
    public void getAsPathShouldRethrownFailure() {
        prepareForResolveWithErrors();
        configuration.resolve();
    }

    @Test
    public void resolve() {
        final Set<File> fileSet = toSet(new File("somePath"));
        makeResolveReturnFileSet(fileSet);
        assertThat(configuration.resolve(), equalTo(fileSet));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test
    public void filesWithDependencies() {
        final Set<File> fileSet = toSet(new File("somePath"));
        prepareForFilesBySpec(fileSet);
        assertThat(configuration.files(context.mock(Dependency.class)), equalTo(fileSet));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test
    public void fileCollectionWithDependencies() {
        Dependency dependency1 = createDependency("group1", "name", "version");
        Dependency dependency2 = createDependency("group2", "name", "version");
        DefaultConfiguration.ConfigurationFileCollection fileCollection = (DefaultConfiguration.ConfigurationFileCollection)
                configuration.fileCollection(dependency1);
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(dependency1),
                equalTo(true));
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(dependency2),
                equalTo(false));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void filesWithSpec() {
        final Set<File> fileSet = toSet(new File("somePath"));
        prepareForFilesBySpec(fileSet);
        assertThat(configuration.files(context.mock(Spec.class)), equalTo(fileSet));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test
    public void fileCollectionWithSpec() {
        @SuppressWarnings("unchecked")
        Spec<Dependency> spec = context.mock(Spec.class);
        DefaultConfiguration.ConfigurationFileCollection fileCollection = (DefaultConfiguration.ConfigurationFileCollection)
                configuration.fileCollection(spec);
        assertThat(fileCollection.getDependencySpec(), sameInstance((Object)spec));
    }

    @Test
    public void filesWithClosureSpec() {
        Closure closure = HelperUtil.toClosure("{ dep -> dep.group == 'group1' }");
        final Set<File> fileSet = toSet(new File("somePath"));
        prepareForFilesBySpec(fileSet);
        assertThat(configuration.files(closure), equalTo(fileSet));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test
    public void fileCollectionWithClosureSpec() {
        Closure closure = HelperUtil.toClosure("{ dep -> dep.group == 'group1' }");
        DefaultConfiguration.ConfigurationFileCollection fileCollection = (DefaultConfiguration.ConfigurationFileCollection)
                configuration.fileCollection(closure);
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(createDependency("group1", "name", "version")),
                equalTo(true));
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(createDependency("group2", "name", "version")),
                equalTo(false));
    }

    @SuppressWarnings("unchecked")
    private void prepareForFilesBySpec(final Set<File> fileSet) {
        final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
        prepareResolve(resolvedConfiguration, false);
        context.checking(new Expectations() {{
            one(resolvedConfiguration).getFiles(with(any(Spec.class)));
            will(returnValue(fileSet));
        }});
    }

    @Test(expected = GradleException.class)
    public void resolveShouldRethrowFailure() {
        prepareForResolveWithErrors();
        configuration.resolve();
    }

    private void prepareForResolveWithErrors() {
        final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
        prepareResolve(resolvedConfiguration, true);
        context.checking(new Expectations(){{
            one(resolvedConfiguration).rethrowFailure();
            will(throwException(new GradleException()));
        }});
    }

    private void makeResolveReturnFileSet(final Set<File> fileSet) {
        final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
        context.checking(new Expectations() {{
            prepareResolve(resolvedConfiguration, false);
            allowing(resolvedConfiguration).getFiles(Specs.SATISFIES_ALL);
            will(returnValue(fileSet));
        }});
    }

    @Test
    public void resolveSuccessfullyAsResolvedConfiguration() {
        ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
        prepareResolve(resolvedConfiguration, false);
        assertThat(configuration.getResolvedConfiguration(), equalTo(resolvedConfiguration));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    private void prepareResolve(final ResolvedConfiguration resolvedConfiguration, final boolean withErrors) {
        context.checking(new Expectations() {{
            allowing(dependencyResolver).resolve(configuration);
            will(returnValue(new ResolverResults(resolvedConfiguration)));
            allowing(resolvedConfiguration).hasError();
            will(returnValue(withErrors));
        }});
    }

    @Test
    public void multipleResolvesShouldUseCachedResult() {
        prepareResolve(context.mock(ResolvedConfiguration.class), true);
        assertThat(configuration.getResolvedConfiguration(), sameInstance(configuration.getResolvedConfiguration()));
    }

    @Test
    public void uploadTaskName() {
        assertThat(configuration.getUploadTaskName(), equalTo("uploadName"));
    }

    private DefaultConfiguration createNamedConfiguration(String confName) {
        return new DefaultConfiguration(confName, confName, configurationContainer,
                dependencyResolver, listenerManager, metaDataProvider, new DefaultResolutionStrategy());
    }
    
    private DefaultConfiguration createNamedConfiguration(String path, String confName) {
        return new DefaultConfiguration(path, confName, configurationContainer,
                dependencyResolver, listenerManager, metaDataProvider, new DefaultResolutionStrategy());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void buildArtifacts() {
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        final Task artifactTaskMock = context.mock(Task.class, "artifactTask");
        final Configuration otherConfiguration = context.mock(Configuration.class);
        final TaskDependency otherArtifactTaskDependencyMock = context.mock(TaskDependency.class, "otherConfTaskDep");
        final PublishArtifact otherArtifact = context.mock(PublishArtifact.class, "otherArtifact");
        final PublishArtifactSet inheritedArtifacts = new DefaultPublishArtifactSet("artifacts", toDomainObjectSet(PublishArtifact.class, otherArtifact));
        DefaultPublishArtifact artifact = createPublishArtifact("name1", "ext1", "type1", "classifier1");
        artifact.builtBy(artifactTaskMock);
        configuration.getArtifacts().add(artifact);

        context.checking(new Expectations() {{
            allowing(otherConfiguration).getHierarchy();
            will(returnValue(toSet()));

            allowing(otherConfiguration).getAllArtifacts();
            will(returnValue(inheritedArtifacts));

            allowing(otherConfiguration).getAllDependencies();

            allowing(otherArtifact).getBuildDependencies();
            will(returnValue(otherArtifactTaskDependencyMock));
            
            allowing(otherArtifactTaskDependencyMock).getDependencies(with(any(Task.class)));
            will(returnValue(toSet(otherConfTaskMock)));
        }});
        configuration.setExtendsFrom(toSet(otherConfiguration));
        assertThat((Set<Task>) configuration.getAllArtifacts().getBuildDependencies().getDependencies(context.mock(Task.class, "caller")),
                equalTo(toSet(artifactTaskMock, otherConfTaskMock)));
    }

    @Test
    public void getAllArtifactFiles() {
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        final Task artifactTaskMock = context.mock(Task.class, "artifactTask");
        final Configuration otherConfiguration = context.mock(Configuration.class);
        final TaskDependency otherConfTaskDependencyMock = context.mock(TaskDependency.class, "otherConfTaskDep");
        final TaskDependency artifactTaskDependencyMock = context.mock(TaskDependency.class, "artifactTaskDep");
        final File artifactFile1 = new File("artifact1");
        final File artifactFile2 = new File("artifact2");
        final PublishArtifact artifact = context.mock(PublishArtifact.class, "artifact");
        final PublishArtifact otherArtifact = context.mock(PublishArtifact.class, "otherArtifact");
        final PublishArtifactSet otherArtifacts = new DefaultPublishArtifactSet("artifacts", toDomainObjectSet(PublishArtifact.class, otherArtifact));

        context.checking(new Expectations() {{
            allowing(otherConfiguration).getHierarchy();
            will(returnValue(toSet()));

            allowing(otherConfiguration).getAllArtifacts();
            will(returnValue(otherArtifacts));

            allowing(otherConfiguration).getAllDependencies();

            allowing(otherConfiguration).getExtendsFrom();
            will(returnValue(toSet()));

            allowing(otherConfiguration).getArtifacts();
            will(returnValue(toSet(otherArtifact)));

            allowing(otherConfTaskDependencyMock).getDependencies(with(any(Task.class)));
            will(returnValue(toSet(otherConfTaskMock)));

            allowing(artifactTaskDependencyMock).getDependencies(with(any(Task.class)));
            will(returnValue(toSet(artifactTaskMock)));

            allowing(artifact).getFile();
            will(returnValue(artifactFile1));

            allowing(otherArtifact).getFile();
            will(returnValue(artifactFile2));

            allowing(artifact).getBuildDependencies();
            will(returnValue(artifactTaskDependencyMock));

            allowing(otherArtifact).getBuildDependencies();
            will(returnValue(otherConfTaskDependencyMock));
        }});

        configuration.getArtifacts().add(artifact);
        configuration.setExtendsFrom(toSet(otherConfiguration));

        FileCollection files = configuration.getAllArtifacts().getFiles();
        assertThat(files.getFiles(), equalTo(toSet(artifactFile1, artifactFile2)));
        assertThat(files.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(otherConfTaskMock, artifactTaskMock)));
    }

    @Test
    public void buildDependenciesDelegatesToAllSelfResolvingDependencies() {
        final Task target = context.mock(Task.class, "target");
        final Task projectDepTaskDummy = context.mock(Task.class, "projectDepTask");
        final Task fileDepTaskDummy = context.mock(Task.class, "fileDepTask");
        final ProjectDependency projectDependencyStub = context.mock(ProjectDependency.class);
        final FileCollectionDependency fileCollectionDependencyStub = context.mock(FileCollectionDependency.class);

        context.checking(new Expectations() {{
            TaskDependency projectTaskDependencyDummy = context.mock(TaskDependency.class, "projectDep");
            TaskDependency fileTaskDependencyStub = context.mock(TaskDependency.class, "fileDep");

            allowing(projectDependencyStub).getBuildDependencies();
            will(returnValue(projectTaskDependencyDummy));

            allowing(projectTaskDependencyDummy).getDependencies(target);
            will(returnValue(toSet(projectDepTaskDummy)));

            allowing(fileCollectionDependencyStub).getBuildDependencies();
            will(returnValue(fileTaskDependencyStub));

            allowing(fileTaskDependencyStub).getDependencies(target);
            will(returnValue(toSet(fileDepTaskDummy)));
        }});

        configuration.getDependencies().add(projectDependencyStub);
        configuration.getDependencies().add(fileCollectionDependencyStub);

        assertThat(configuration.getBuildDependencies().getDependencies(target), equalTo((Set) toSet(fileDepTaskDummy,
                projectDepTaskDummy)));
    }

    @Test
    public void buildDependenciesDelegatesToInheritedConfigurations() {
        final Task target = context.mock(Task.class, "target");
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        final TaskDependency dependencyTaskDependencyStub = context.mock(TaskDependency.class, "otherConfTaskDep");
        final Configuration otherConfiguration = context.mock(Configuration.class, "otherConf");
        final FileCollectionDependency fileCollectionDependencyStub = context.mock(FileCollectionDependency.class);
        final DependencySet inherited = new DefaultDependencySet("dependencies", toDomainObjectSet(Dependency.class, fileCollectionDependencyStub));

        context.checking(new Expectations() {{
            allowing(otherConfiguration).getHierarchy();
            will(returnValue(toSet()));

            allowing(otherConfiguration).getAllArtifacts();

            allowing(otherConfiguration).getAllDependencies();
            will(returnValue(inherited));

            allowing(fileCollectionDependencyStub).getBuildDependencies();
            will(returnValue(dependencyTaskDependencyStub));

            allowing(dependencyTaskDependencyStub).getDependencies(target);
            will(returnValue(toSet(otherConfTaskMock)));
        }});

        configuration.extendsFrom(otherConfiguration);

        assertThat(configuration.getBuildDependencies().getDependencies(target), equalTo((Set) toSet(otherConfTaskMock)));
    }

    @SuppressWarnings("unchecked")
    @Test public void taskDependencyFromProjectDependencyUsingNeeded() {
        Configuration superConfig = createNamedConfiguration("superConf");
        configuration.extendsFrom(superConfig);

        final ProjectDependency projectDependencyStub = context.mock(ProjectDependency.class);
        superConfig.getDependencies().add(projectDependencyStub);

        final Project projectStub = context.mock(Project.class);
        final TaskContainer taskContainerStub = context.mock(TaskContainer.class);
        final Task taskStub = context.mock(Task.class);
        final String taskName = "testit";

        context.checking(new Expectations() {{
            allowing(projectDependencyStub).getDependencyProject(); will(returnValue(projectStub));
            allowing(projectStub).getTasks(); will(returnValue(taskContainerStub));
            allowing(taskContainerStub).findByName(taskName); will(returnValue(taskStub));
        }});

        TaskDependency td = configuration.getTaskDependencyFromProjectDependency(true, taskName);
        Task unusedTask = context.mock(Task.class, "unused");

        assertThat((Set<Task>) td.getDependencies(unusedTask), equalTo(toSet(taskStub)));
    }

    @SuppressWarnings("unchecked")
    @Test public void taskDependencyFromProjectDependencyUsingDependents() {
        final String configName = configuration.getName();
        final String taskName = "testit";
        final Task tdTask = context.mock(Task.class, "tdTask");
        final Project taskProject = context.mock(Project.class, "taskProject");
        final Project rootProject = context.mock(Project.class, "rootProject");
        final Project dependentProject = context.mock(Project.class, "dependentProject");
        final Task desiredTask = context.mock(Task.class, "desiredTask");
        final Set<Task> taskSet = toSet(desiredTask);
        final ConfigurationContainer configurationContainer = context.mock(ConfigurationContainer.class);
        final Configuration dependentConfig = context.mock(Configuration.class);
        final ProjectDependency projectDependency = context.mock(ProjectDependency.class);
        final Set<ProjectDependency> projectDependencies = toDomainObjectSet(ProjectDependency.class, projectDependency);
        final DependencySet otherDependencies = context.mock(DependencySet.class);

        context.checking(new Expectations() {{
            allowing(tdTask).getProject(); will(returnValue(taskProject));
            allowing(taskProject).getRootProject(); will(returnValue(rootProject));
            allowing(rootProject).getTasksByName(taskName, true); will(returnValue(taskSet));
            allowing(desiredTask).getProject(); will(returnValue(dependentProject));
            allowing(dependentProject).getConfigurations(); will(returnValue(configurationContainer));
            allowing(configurationContainer).findByName(configName); will(returnValue(dependentConfig));

            allowing(dependentConfig).getAllDependencies(); will(returnValue(otherDependencies));
            allowing(otherDependencies).withType(ProjectDependency.class); will(returnValue(projectDependencies));
            allowing(projectDependency).getDependencyProject(); will(returnValue(taskProject));
        }});

        TaskDependency td = configuration.getTaskDependencyFromProjectDependency(false, taskName);
        assertThat((Set<Task>) td.getDependencies(tdTask), equalTo(toSet(desiredTask)));
    }

    @SuppressWarnings("unchecked")
    @Test public void taskDependencyFromProjectDependencyWithoutCommonConfiguration() {
        // This test exists because a NullPointerException was thrown by
        // getTaskDependencyFromProjectDependency() if the rootProject
        // defined a task as the same name as a subproject's task, but did
        // not define the same configuration.
        final String configName = configuration.getName();
        final String taskName = "testit";
        final Task tdTask = context.mock(Task.class, "tdTask");
        final Project taskProject = context.mock(Project.class, "taskProject");
        final Project rootProject = context.mock(Project.class, "rootProject");
        final Project dependentProject = context.mock(Project.class, "dependentProject");
        final Task desiredTask = context.mock(Task.class, "desiredTask");
        final Set<Task> taskSet = toSet(desiredTask);
        final ConfigurationContainer configurationContainer = context.mock(ConfigurationContainer.class);

        context.checking(new Expectations() {{
            allowing(tdTask).getProject(); will(returnValue(taskProject));
            allowing(taskProject).getRootProject(); will(returnValue(rootProject));
            allowing(rootProject).getTasksByName(taskName, true); will(returnValue(taskSet));
            allowing(desiredTask).getProject(); will(returnValue(dependentProject));
            allowing(dependentProject).getConfigurations(); will(returnValue(configurationContainer));

            // return null to mock not finding the given configuration
            allowing(configurationContainer).findByName(configName); will(returnValue(null));
        }});

        TaskDependency td = configuration.getTaskDependencyFromProjectDependency(false, taskName);
        assertThat(td.getDependencies(tdTask), equalTo(Collections.EMPTY_SET));
    }


    @Test
    public void getDependencies() {
        Dependency dependency = context.mock(Dependency.class);
        configuration.getDependencies().add(dependency);
        assertThat(configuration.getDependencies(), hasSameItems(toSet(dependency)));
    }

    @Test
    public void getTypedDependencies() {
        ProjectDependency projectDependency = context.mock(ProjectDependency.class);
        configuration.getDependencies().add(context.mock(Dependency.class));
        configuration.getDependencies().add(projectDependency);
        assertThat(configuration.getDependencies().withType(ProjectDependency.class), hasSameItems(toSet(projectDependency)));
    }

    @Test
    public void getTypedDependenciesReturnsEmptySetWhenNoMatches() {
        configuration.getDependencies().add(context.mock(Dependency.class));
        assertThat(configuration.getDependencies().withType(ProjectDependency.class), isEmpty());
    }

    @Test
    public void getAllDependencies() {
        Dependency dependencyConf = createDependency("group1", "name1", "version1");
        Dependency dependencyOtherConf1 = createDependency("group1", "name1", "version1");
        Dependency dependencyOtherConf2 = context.mock(Dependency.class, "dep2");
        Configuration otherConf = createNamedConfiguration("otherConf");
        configuration.getDependencies().add(dependencyConf);
        configuration.extendsFrom(otherConf);
        otherConf.getDependencies().add(dependencyOtherConf1);
        otherConf.getDependencies().add(dependencyOtherConf2);

        assertThat(configuration.getAllDependencies(), hasSameItems(toLinkedSet(dependencyConf, dependencyOtherConf2)));
        assertCorrectInstanceInAllDependencies(configuration.getAllDependencies(), dependencyConf);
    }

    @Test
    public void getAllTypedDependencies() {
        ProjectDependency projectDependencyCurrentConf = context.mock(ProjectDependency.class, "projectDepCurrentConf");
        configuration.getDependencies().add(context.mock(Dependency.class, "depCurrentConf"));
        configuration.getDependencies().add(projectDependencyCurrentConf);
        Configuration otherConf = createNamedConfiguration("otherConf");
        configuration.extendsFrom(otherConf);
        ProjectDependency projectDependencyExtendedConf = context.mock(ProjectDependency.class, "projectDepExtendedConf");
        otherConf.getDependencies().add(context.mock(Dependency.class, "depExtendedConf"));
        otherConf.getDependencies().add(projectDependencyExtendedConf);

        assertThat(configuration.getAllDependencies().withType(ProjectDependency.class), hasSameItems(toLinkedSet(projectDependencyCurrentConf, projectDependencyExtendedConf)));
    }

    @Test
    public void getAllTypedDependenciesReturnsEmptySetWhenNoMatches() {
        configuration.getDependencies().add(context.mock(Dependency.class, "depCurrentConf"));
        Configuration otherConf = createNamedConfiguration("otherConf");
        configuration.extendsFrom(otherConf);
        otherConf.getDependencies().add(context.mock(Dependency.class, "depExtendedConf"));

        assertThat(configuration.getAllDependencies().withType(ProjectDependency.class), isEmpty());
    }

    @Test
    public void getAllArtifacts() {
        PublishArtifact artifactConf = createPublishArtifact("name1", "ext1", "type1", "classifier1");
        PublishArtifact artifactOtherConf2 = createPublishArtifact("name2", "ext2", "type2", "classifier2");
        Configuration otherConf = createNamedConfiguration("otherConf");
        configuration.getArtifacts().add(artifactConf);
        configuration.extendsFrom(otherConf);
        otherConf.getArtifacts().add(artifactOtherConf2);
        assertThat(configuration.getAllArtifacts(), hasSameItems(toLinkedSet(artifactConf, artifactOtherConf2)));
    }

    @Test
    public void artifactAddedAction() {
        final TestClosure closure = context.mock(TestClosure.class);
        final PublishArtifact artifact = createPublishArtifact("name1", "ext1", "type1", "classifier1");

        context.checking(new Expectations() {{
            one(closure).call(artifact);
        }});

        configuration.getArtifacts().whenObjectAdded(HelperUtil.toClosure(closure));
        configuration.getArtifacts().add(artifact);
    }

    @Test
    public void artifactRemovedAction() {
        final TestClosure closure = context.mock(TestClosure.class);
        final PublishArtifact artifact = createPublishArtifact("name1", "ext1", "type1", "classifier1");

        configuration.getArtifacts().add(artifact);

        context.checking(new Expectations() {{
            one(closure).call(artifact);
        }});

        configuration.getArtifacts().whenObjectRemoved(HelperUtil.toClosure(closure));

        configuration.getArtifacts().remove(artifact);
    }

    @Test
    public void removeArtifact() {
        PublishArtifact artifact = createPublishArtifact("name1", "ext1", "type1", "classifier1");
        configuration.getArtifacts().add(artifact);
        configuration.getArtifacts().remove(artifact);
        assertThat(configuration.getAllArtifacts(), Matchers.isEmpty());
    }

    @Test
    public void removeArtifactWithUnknownArtifact() {
        PublishArtifact artifact = createPublishArtifact("name1", "ext1", "type1", "classifier1");
        configuration.getArtifacts().add(artifact);
        configuration.getArtifacts().remove(createPublishArtifact("name2", "ext1", "type1", "classifier1"));
        assertThat(configuration.getAllArtifacts(), hasSameItems(toSet(artifact)));
    }

    private void assertCorrectInstanceInAllDependencies(Set<Dependency> allDependencies, Dependency correctInstance) {
        for (Dependency dependency : allDependencies) {
            if (dependency == correctInstance) {
                return;
            }
        }
        fail("Correct instance is missing!");
    }

    @Test
    public void copy() {
        prepareConfigurationForCopyTest();

        Configuration copiedConfiguration = configuration.copy();

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, configuration.getDependencies());
    }

    @Test
    public void copyWithSpec() {
        prepareConfigurationForCopyTest();
        Set<Dependency> expectedDependenciesToCopy = new HashSet<Dependency>(configuration.getDependencies());
        configuration.getDependencies().add(createDependency("group3", "name3", "version3"));

        Configuration copiedConfiguration = configuration.copy(new Spec<Dependency>() {
            public boolean isSatisfiedBy(Dependency element) {
                return !element.getGroup().equals("group3");
            }
        });

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    @Test
    public void copyWithClosure() {
        prepareConfigurationForCopyTest();
        Set<Dependency> expectedDependenciesToCopy = new HashSet<Dependency>(configuration.getDependencies());
        configuration.getDependencies().add(createDependency("group3", "name3", "version3"));

        Closure specClosure = HelperUtil.toClosure("{ element ->  !element.group.equals(\"group3\")}");
        Configuration copiedConfiguration = configuration.copy(specClosure);

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    private void prepareConfigurationForCopyTest() {
        configuration.setVisible(false);
        configuration.setTransitive(false);
        configuration.setDescription("descript");
        configuration.exclude(toMap("group", "value"));
        configuration.exclude(toMap("group", "value2"));
        configuration.getArtifacts().add(createPublishArtifact("name1", "ext1", "type1", "classifier1"));
        configuration.getArtifacts().add(createPublishArtifact("name2", "ext2", "type2", "classifier2"));
        configuration.getDependencies().add(createDependency("group1", "name1", "version1"));
        configuration.getDependencies().add(createDependency("group2", "name2", "version2"));
    }

    private void assertThatCopiedConfigurationHasElementsAndName(Configuration copiedConfiguration, Set<Dependency> expectedDependencies) {
        assertThat(copiedConfiguration.getName(), equalTo(configuration.getName() + "Copy"));
        assertThat(copiedConfiguration.isVisible(), equalTo(configuration.isVisible()));
        assertThat(copiedConfiguration.isTransitive(), equalTo(configuration.isTransitive()));
        assertThat(copiedConfiguration.getDescription(), equalTo(configuration.getDescription()));
        assertThat(asSet(copiedConfiguration.getAllArtifacts()), equalTo(asSet(configuration.getAllArtifacts())));
        assertThat(copiedConfiguration.getExcludeRules(), equalTo(configuration.getExcludeRules()));
        assertThat(copiedConfiguration.getExcludeRules().iterator().next(), not(sameInstance(configuration.getExcludeRules().iterator().next())));
        assertThat(WrapUtil.asSet(copiedConfiguration.getDependencies()), equalTo(WrapUtil.asSet(expectedDependencies)));
        assertNotSameInstances(copiedConfiguration.getDependencies(), expectedDependencies);
    }

    @Test
    public void copyRecursive() {
        prepareConfigurationForCopyRecursiveTest();

        Configuration copiedConfiguration = configuration.copyRecursive();

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, configuration.getAllDependencies());
    }

    @Test
    public void copyRecursiveWithSpec() {
        prepareConfigurationForCopyRecursiveTest();
        Set<Dependency> expectedDependenciesToCopy = new HashSet<Dependency>(configuration.getAllDependencies());
        configuration.getDependencies().add(createDependency("group3", "name3", "version3"));

        Closure specClosure = HelperUtil.toClosure("{ element ->  !element.group.equals(\"group3\")}");
        Configuration copiedConfiguration = configuration.copyRecursive(specClosure);

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    @Test
    public void copyRecursiveWithClosure() {
        prepareConfigurationForCopyRecursiveTest();
        Set<Dependency> expectedDependenciesToCopy = new HashSet<Dependency>(configuration.getAllDependencies());
        configuration.getDependencies().add(createDependency("group3", "name3", "version3"));

        Configuration copiedConfiguration = configuration.copyRecursive(new Spec<Dependency>() {
            public boolean isSatisfiedBy(Dependency element) {
                return !element.getGroup().equals("group3");
            }
        });

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    private void prepareConfigurationForCopyRecursiveTest() {
        prepareConfigurationForCopyTest();
        Dependency similarDependency2InOtherConf = createDependency("group2", "name2", "version2");
        Dependency otherConfDependency = createDependency("group4", "name4", "version4");
        Configuration otherConf = createNamedConfiguration("otherConf");
        otherConf.getDependencies().add(similarDependency2InOtherConf);
        otherConf.getDependencies().add(otherConfDependency);
        configuration.extendsFrom(otherConf);
    }

    private void assertNotSameInstances(Set<Dependency> dependencies, Set<Dependency> otherDependencies) {
        for (Dependency dependency : dependencies) {
            assertHasEqualButNotSameInstance(dependency, otherDependencies);
        }
    }

    private void assertHasEqualButNotSameInstance(Dependency dependency, Set<Dependency> otherDependencies) {
        assertThat(otherDependencies, hasItem(dependency));
        for (Dependency otherDependency : otherDependencies) {
            if (otherDependency.equals(dependency)) {
                assertThat(otherDependency, not(sameInstance(dependency)));
            }
        }
    }

    @Test
    public void propertyChangeWithNonUnresolvedStateShouldThrowEx() {
        makeResolveReturnFileSet(new HashSet<File>());
        configuration.resolve();
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.setTransitive(true);
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.setDescription("someDesc");
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.setExcludeRules(new HashSet<ExcludeRule>());
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.setExtendsFrom(new HashSet<Configuration>());
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.setVisible(true);
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.getDependencies().add(context.mock(Dependency.class));
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.getDependencies().add(context.mock(Dependency.class));
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.exclude(new HashMap<String, String>());
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.extendsFrom(context.mock(Configuration.class));
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.getArtifacts().add(context.mock(PublishArtifact.class));
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.getArtifacts().remove(context.mock(PublishArtifact.class, "removeArtifact"));
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.getArtifacts().add(context.mock(PublishArtifact.class, "removeArtifact"));
            }
        });
    }
    
    @Test
    public void dumpString() {
        Dependency configurationDependency = createDependency("dumpgroup1", "dumpname1", "dumpversion1");
        Dependency otherConfSimilarDependency = createDependency("dumpgroup1", "dumpname1", "dumpversion1");
        Dependency otherConfDependency = createDependency("dumpgroup2", "dumpname2", "dumpversion2");
        Configuration otherConf = createNamedConfiguration("dumpConf");
        configuration.extendsFrom(otherConf);
        otherConf.getDependencies().add(otherConfDependency);
        otherConf.getDependencies().add(otherConfSimilarDependency);
        configuration.getDependencies().add(configurationDependency);

        assertThat(configuration.dump(),
                containsString(
                "\nConfiguration:"
                + "  class='class org.gradle.api.internal.artifacts.configurations.DefaultConfiguration'"
                + "  name='name'"
                + "  hashcode='"+ configuration.hashCode() +"'"
                + "\nLocal Dependencies:"
                + "\n   DefaultExternalModuleDependency{group='dumpgroup1', name='dumpname1', version='dumpversion1', configuration='default'}"
                + "\nLocal Artifacts:"
                + "\n   none"
                + "\nAll Dependencies:"
                + "\n   DefaultExternalModuleDependency{group='dumpgroup1', name='dumpname1', version='dumpversion1', configuration='default'}"
                + "\n   DefaultExternalModuleDependency{group='dumpgroup2', name='dumpname2', version='dumpversion2', configuration='default'}"
                + "\nAll Artifacts:"
                + "\n   none"));
    }

    ModuleDependency createDependency(String group, String name, String version) {
        return new DefaultExternalModuleDependency(group, name, version);
    }

    DefaultPublishArtifact createPublishArtifact(String name, String extension, String type, String classifier) {
        return new DefaultPublishArtifact(name, extension, type, classifier, new Date(), new File(""));
    }

    private void assertInvalidUserDataException(Executer executer) {
        try {
            executer.execute();
            fail();
        } catch (InvalidUserDataException e) {
            // ignore
        }
    }

    private static interface Executer {
        void execute();
    }
}