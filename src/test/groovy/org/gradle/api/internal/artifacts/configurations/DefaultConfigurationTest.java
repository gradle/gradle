/*
 * Copyright 2007, 2008 the original author or authors.
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
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.HelperUtil;
import static org.gradle.util.Matchers.isEmpty;
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

@RunWith(JMock.class)
public class DefaultConfigurationTest {
    private static final String CONF_NAME = "confName";

    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private IvyService ivyServiceStub = context.mock(IvyService.class);
    private DefaultConfigurationContainer configurationContainer;
    private ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction =
            new ProjectDependenciesBuildInstruction(null);

    private DefaultConfiguration configuration;

    @Before
    public void setUp() {
        configurationContainer = new DefaultConfigurationContainer(ivyServiceStub, projectDependenciesBuildInstruction);
        configuration = (DefaultConfiguration) configurationContainer.add(CONF_NAME);
    }

    @Test
    public void defaultValues() {
        DefaultConfiguration configuration = new DefaultConfiguration(CONF_NAME, configurationContainer, ivyServiceStub,
                projectDependenciesBuildInstruction);
        assertThat(configuration.getName(), equalTo(CONF_NAME));
        assertThat(configuration.isVisible(), equalTo(true));
        assertThat(configuration.getExtendsFrom().size(), equalTo(0));
        assertThat(configuration.isTransitive(), equalTo(true));
        assertThat(configuration.getDescription(), nullValue());
        assertThat(configuration.getState(), equalTo(Configuration.State.UNRESOLVED));
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
        Map<String, String> excludeArgs1 = toMap("org", "value");
        Map<String, String> excludeArgs2 = toMap("org2", "value2");
        assertThat(configuration.exclude(excludeArgs1), sameInstance(configuration));
        configuration.exclude(excludeArgs2);
        assertThat(configuration.getExcludeRules(), equalTo(WrapUtil.<ExcludeRule>toSet(
                new DefaultExcludeRule(excludeArgs1), new DefaultExcludeRule(excludeArgs2))));
    }

    @Test
    public void setExclude() {
        Set<ExcludeRule> excludeRules = WrapUtil.<ExcludeRule>toSet(new DefaultExcludeRule(toMap("org", "value")));
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
        Configuration configuration1 = configurationContainer.add("otherConf1");
        configuration.extendsFrom(configuration1);
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1)));

        Configuration configuration2 = configurationContainer.add("otherConf2");
        configuration.extendsFrom(configuration2);
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1, configuration2)));
    }

    @Test
    public void setExtendsFrom() {
        Configuration configuration1 = configurationContainer.add("otherConf1");

        configuration.setExtendsFrom(toSet(configuration1));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1)));

        Configuration configuration2 = configurationContainer.add("otherConf2");
        configuration.setExtendsFrom(toSet(configuration2));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration2)));
    }

    @Test(expected = InvalidUserDataException.class)
    public void extendsFromWithDirectCycle_shouldThrowInvalidUserDataEx() {
        Configuration otherConfiguration = configurationContainer.add("otherConf");
        otherConfiguration.extendsFrom(configuration);
        configuration.extendsFrom(otherConfiguration);
    }

    @Test(expected = InvalidUserDataException.class)
    public void extendsFromWithIndirectCycle_shouldThrowInvalidUserDataEx() {
        Configuration otherConfiguration1 = configurationContainer.add("otherConf1");
        Configuration otherConfiguration2 = configurationContainer.add("otherConf2");
        configuration.extendsFrom(otherConfiguration1);
        otherConfiguration1.extendsFrom(otherConfiguration2);
        otherConfiguration2.extendsFrom(configuration);
    }

    @Test(expected = InvalidUserDataException.class)
    public void setExtendsFromWithCycle_shouldThrowInvalidUserDataEx() {
        Configuration otherConfiguration = configurationContainer.add("otherConf");
        otherConfiguration.extendsFrom(configuration);
        configuration.setExtendsFrom(toSet(otherConfiguration));
    }

    @Test
    public void getHierarchy() {
        Configuration root1 = configurationContainer.add("root1");
        Configuration middle1 = configurationContainer.add("middle1").extendsFrom(root1);
        Configuration root2 = configurationContainer.add("root2");
        Configuration middle2 = configurationContainer.add("middle2").extendsFrom(root1, root2);
        configurationContainer.add("root3");
        Configuration leaf = configurationContainer.add("leaf1").extendsFrom(middle1, middle2);
        List<Configuration> hierarchy = leaf.getHierarchy();
        assertThat(hierarchy.size(), equalTo(5));
        assertThat(hierarchy.get(0), equalTo(leaf));
        assertBothExistsAndOneIsBeforeOther(hierarchy, middle1, root1);
        assertBothExistsAndOneIsBeforeOther(hierarchy, middle2, root2);
    }

    private void assertBothExistsAndOneIsBeforeOther(List<Configuration> hierarchy, Configuration beforeConf, Configuration afterConf) {
        assertThat(hierarchy, hasItem(beforeConf));
        assertThat(hierarchy, hasItem(afterConf));
        assertThat(hierarchy.indexOf(beforeConf), lessThan(hierarchy.indexOf(afterConf)));
    }

    @Test
    public void getAll() {
        String testConf1 = "testConf1";
        String testConf2 = "testConf2";
        configurationContainer.add(testConf1);
        configurationContainer.add(testConf2);
        assertThat(Configurations.getNames(configuration.getAll()), equalTo(toSet(CONF_NAME, testConf1, testConf2)));
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
        Dependency dependency1 = HelperUtil.createDependency("group1", "name", "version");
        Dependency dependency2 = HelperUtil.createDependency("group2", "name", "version");
        DefaultConfiguration.ConfigurationFileCollection fileCollection = (DefaultConfiguration.ConfigurationFileCollection)
                configuration.fileCollection(dependency1);
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(dependency1),
                equalTo(true));
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(dependency2),
                equalTo(false));
    }

    @Test
    public void filesWithSpec() {
        final Set<File> fileSet = toSet(new File("somePath"));
        prepareForFilesBySpec(fileSet);
        assertThat(configuration.files(context.mock(Spec.class)), equalTo(fileSet));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test
    public void fileCollectionWithSpec() {
        Spec spec = context.mock(Spec.class);
        DefaultConfiguration.ConfigurationFileCollection fileCollection = (DefaultConfiguration.ConfigurationFileCollection)
                configuration.fileCollection(spec);
        assertThat(fileCollection.getDependencySpec(), sameInstance(spec));
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
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(HelperUtil.createDependency("group1", "name", "version")),
                equalTo(true));
        assertThat(fileCollection.getDependencySpec().isSatisfiedBy(HelperUtil.createDependency("group2", "name", "version")),
                equalTo(false));
    }

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
            allowing(ivyServiceStub).resolve(configuration);
            will(returnValue(resolvedConfiguration));
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
    public void publish() {
        final Configuration otherConfiguration = configurationContainer.add("testConf").extendsFrom(configuration);
        final PublishInstruction publishInstruction = createAnonymousPublishInstruction();
        final List<DependencyResolver> dependencyResolvers = toList(context.mock(DependencyResolver.class, "publish"));
        context.checking(new Expectations() {{
            allowing(ivyServiceStub).publish(new LinkedHashSet(otherConfiguration.getHierarchy()), publishInstruction, dependencyResolvers);
        }});
        otherConfiguration.publish(dependencyResolvers, publishInstruction);
    }

    @Test
    public void uploadInternalTaskName() {
        assertThat(configuration.getUploadInternalTaskName(), equalTo("uploadConfNameInternal"));
    }

    @Test
    public void uploadTaskName() {
        assertThat(configuration.getUploadTaskName(), equalTo("uploadConfName"));
    }

    private PublishInstruction createAnonymousPublishInstruction() {
        return new PublishInstruction();
    }

    @Test
    public void equality() {
        Configuration differentConf = configurationContainer.add(CONF_NAME + "delta");
        assertThat(configuration.equals(differentConf), equalTo(false));

        assertThat(configuration.equals(createNamedConfiguration(CONF_NAME)), equalTo(true));
        assertThat(configuration.hashCode(), equalTo(createNamedConfiguration(CONF_NAME).hashCode()));
    }

    private DefaultConfiguration createNamedConfiguration(String confName) {
        return new DefaultConfiguration(confName, configurationContainer, ivyServiceStub,
                projectDependenciesBuildInstruction);
    }

    @Test
    public void buildArtifacts() {
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        final Task artifactTaskMock = context.mock(Task.class, "artifactTask");
        final Configuration otherConfiguration = context.mock(Configuration.class);
        final TaskDependency otherConfTaskDependencyMock = context.mock(TaskDependency.class, "otherConfTaskDep");
        final TaskDependency artifactTaskDependencyMock = context.mock(TaskDependency.class, "artifactTaskDep");
        DefaultPublishArtifact artifact = HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1");
        artifact.setTaskDependency(artifactTaskDependencyMock);
        configuration.addArtifact(artifact);

        context.checking(new Expectations() {{
            allowing(otherConfiguration).getBuildArtifacts();
            will(returnValue(otherConfTaskDependencyMock));

            allowing(otherConfiguration).getHierarchy();
            will(returnValue(toList()));

            allowing(otherConfTaskDependencyMock).getDependencies(with(any(Task.class)));
            will(returnValue(toSet(otherConfTaskMock)));

            allowing(artifactTaskDependencyMock).getDependencies(with(any(Task.class)));
            will(returnValue(toSet(artifactTaskMock)));
        }});
        configuration.setExtendsFrom(toSet(otherConfiguration));
        assertThat((Set<Task>) configuration.getBuildArtifacts().getDependencies(context.mock(Task.class, "caller")),
                equalTo(toSet(artifactTaskMock, otherConfTaskMock)));
    }

    @Test
    public void buildDependencies() {
        final Task dependencyProjectUploadTaskStub = context.mock(Task.class, "dependencyProjectUploadTask");
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        prepareBuildDependenciesTest(dependencyProjectUploadTaskStub, otherConfTaskMock,
                new ProjectDependenciesBuildInstruction(Collections.<String>emptyList()), new HashMap<String, Task>());
        assertThat((Set<Task>) configuration.getBuildDependencies().getDependencies(dependencyProjectUploadTaskStub),
                equalTo(toSet(otherConfTaskMock, dependencyProjectUploadTaskStub)));
    }

    @Test
    public void buildDependenciesWithRebuildDisabled() {
        final Task dependencyProjectUploadTaskStub = context.mock(Task.class, "dependencyProjectUploadTask");
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        prepareBuildDependenciesTest(dependencyProjectUploadTaskStub, otherConfTaskMock,
                new ProjectDependenciesBuildInstruction(null), new HashMap<String, Task>());
        assertThat((Set<Task>) configuration.getBuildDependencies().getDependencies(dependencyProjectUploadTaskStub),
                equalTo(WrapUtil.<Task>toSet()));
    }

    @Test
    public void buildDependenciesWithAdditonalProjectDependencyTasks() {
        final Task dependencyProjectUploadTaskStub = context.mock(Task.class, "dependencyProjectUploadTask");
        final Task otherConfTaskMock = context.mock(Task.class, "otherConfTask");
        final Task projectDependencyTaskMock = context.mock(Task.class, "projectDependencyTask");
        String projectDependencyTaskName = "projectDepTask";
        prepareBuildDependenciesTest(dependencyProjectUploadTaskStub, otherConfTaskMock,
                new ProjectDependenciesBuildInstruction(toList(projectDependencyTaskName)),
                toMap(projectDependencyTaskName, projectDependencyTaskMock));
        assertThat((Set<Task>) configuration.getBuildDependencies().getDependencies(dependencyProjectUploadTaskStub),
                equalTo(toSet(otherConfTaskMock, dependencyProjectUploadTaskStub, projectDependencyTaskMock)));
    }

    private void prepareBuildDependenciesTest(final Task dependencyProjectUploadTaskStub, final Task otherConfTaskMock,
                                              ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction,
                                              final Map<String, Task> projectDependencyTasks) {
        configuration.setProjectDependenciesBuildInstruction(projectDependenciesBuildInstruction);
        final TaskDependency otherConfTaskDependencyMock = context.mock(TaskDependency.class, "otherConfTaskDep");
        final Configuration otherConfiguration = context.mock(Configuration.class, "otherConf");

        final Project dependencyProjectStub = context.mock(Project.class);
        final Configuration dependencyProjectConfStub = context.mock(Configuration.class, "dependenctProjectConf");
        final ProjectDependency projectDependencyStub = context.mock(ProjectDependency.class);
        final TaskContainer taskContainer = context.mock(TaskContainer.class);
        context.checking(new Expectations() {{
            String uploadInternalTaskName = "someName";

            allowing(otherConfiguration).getBuildDependencies();
            will(returnValue(otherConfTaskDependencyMock));

            allowing(otherConfiguration).getHierarchy();
            will(returnValue(toList()));

            allowing(projectDependencyStub).getDependencyProject();
            will(returnValue(dependencyProjectStub));

            allowing(projectDependencyStub).getProjectConfiguration();
            will(returnValue(dependencyProjectConfStub));

            allowing(dependencyProjectConfStub).getUploadInternalTaskName();
            will(returnValue(uploadInternalTaskName));

            allowing(dependencyProjectStub).getTasks();
            will(returnValue(taskContainer));

            allowing(taskContainer).getByName(uploadInternalTaskName);
            will(returnValue(dependencyProjectUploadTaskStub));

            for (String taskName : projectDependencyTasks.keySet()) {
                allowing(taskContainer).getByName(taskName);
                will(returnValue(projectDependencyTasks.get(taskName)));
            }

            allowing(otherConfTaskDependencyMock).getDependencies(with(any(Task.class)));
            will(returnValue(toSet(otherConfTaskMock)));
        }});
        configuration.addDependency(projectDependencyStub);
        configuration.setExtendsFrom(toSet(otherConfiguration));
    }

    @Test
    public void getDependencies() {
        Dependency dependency = context.mock(Dependency.class);
        configuration.addDependency(dependency);
        assertThat(configuration.getDependencies(), equalTo(toSet(dependency)));
    }

    @Test
    public void getTypedDependencies() {
        ProjectDependency projectDependency = context.mock(ProjectDependency.class);
        configuration.addDependency(context.mock(Dependency.class));
        configuration.addDependency(projectDependency);
        assertThat(configuration.getDependencies(ProjectDependency.class), equalTo(toSet(projectDependency)));
    }

    @Test
    public void getTypedDependenciesReturnsEmptySetWhenNoMatches() {
        configuration.addDependency(context.mock(Dependency.class));
        assertThat(configuration.getDependencies(ProjectDependency.class), isEmpty());
    }

    @Test
    public void getAllDependencies() {
        Dependency dependencyConf = HelperUtil.createDependency("group1", "name1", "version1");
        Dependency dependencyOtherConf1 = HelperUtil.createDependency("group1", "name1", "version1");
        Dependency dependencyOtherConf2 = context.mock(Dependency.class, "dep2");
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.addDependency(dependencyConf);
        configuration.extendsFrom(otherConf);
        otherConf.addDependency(dependencyOtherConf1);
        otherConf.addDependency(dependencyOtherConf2);

        assertThat(configuration.getAllDependencies(), equalTo(toSet(dependencyConf, dependencyOtherConf2)));
        assertCorrectInstanceInAllDependencies(configuration.getAllDependencies(), dependencyConf);
    }

    @Test
    public void getAllTypedDependencies() {
        ProjectDependency projectDependencyCurrentConf = context.mock(ProjectDependency.class, "projectDepCurrentConf");
        configuration.addDependency(context.mock(Dependency.class, "depCurrentConf"));
        configuration.addDependency(projectDependencyCurrentConf);
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.extendsFrom(otherConf);
        ProjectDependency projectDependencyExtendedConf = context.mock(ProjectDependency.class, "projectDepExtendedConf");
        otherConf.addDependency(context.mock(Dependency.class, "depExtendedConf"));
        otherConf.addDependency(projectDependencyExtendedConf);

        assertThat(configuration.getAllDependencies(ProjectDependency.class), equalTo(toSet(projectDependencyCurrentConf, projectDependencyExtendedConf)));
    }

    @Test
    public void getAllTypedDependenciesReturnsEmptySetWhenNoMatches() {
        configuration.addDependency(context.mock(Dependency.class, "depCurrentConf"));
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.extendsFrom(otherConf);
        otherConf.addDependency(context.mock(Dependency.class, "depExtendedConf"));

        assertThat(configuration.getAllDependencies(ProjectDependency.class), isEmpty());
    }

    @Test
    public void getAllArtifacts() {
        PublishArtifact artifactConf = HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1");
        PublishArtifact artifactOtherConf2 = HelperUtil.createPublishArtifact("name2", "ext2", "type2", "classifier2");
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.addArtifact(artifactConf);
        configuration.extendsFrom(otherConf);
        otherConf.addArtifact(artifactOtherConf2);
        assertThat(configuration.getAllArtifacts(), equalTo(toSet(artifactConf, artifactOtherConf2)));
    }

    @Test
    public void removeArtifact() {
        PublishArtifact artifact = HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1");
        configuration.addArtifact(artifact);
        configuration.removeArtifact(artifact);
        assertThat(configuration.getAllArtifacts(), equalTo(Collections.<PublishArtifact>emptySet()));
    }

    @Test
    public void removeArtifactWithUnknownArtifact() {
        PublishArtifact artifact = HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1");
        configuration.addArtifact(artifact);
        configuration.removeArtifact(HelperUtil.createPublishArtifact("name2", "ext1", "type1", "classifier1"));
        assertThat(configuration.getAllArtifacts(), equalTo(WrapUtil.toSet(artifact)));
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
    public void getConfiguration() {
        Dependency configurationDependency = HelperUtil.createDependency("group1", "name1", "version1");
        Dependency otherConfSimilarDependency = HelperUtil.createDependency("group1", "name1", "version1");
        Dependency otherConfDependency = HelperUtil.createDependency("group2", "name2", "version2");
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.extendsFrom(otherConf);
        otherConf.addDependency(otherConfDependency);
        otherConf.addDependency(otherConfSimilarDependency);
        configuration.addDependency(configurationDependency);

        assertThat((DefaultConfiguration) configuration.getConfiguration(configurationDependency), equalTo(configuration));
        assertThat((DefaultConfiguration) configuration.getConfiguration(otherConfSimilarDependency), equalTo(configuration));
        assertThat(configuration.getConfiguration(otherConfDependency), equalTo(otherConf));
    }

    @Test
    public void getConfigurationWithUnknownDependency() {
        assertThat(configuration.getConfiguration(HelperUtil.createDependency("group1", "name1", "version1")), equalTo(null));
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
        Set<Dependency> expectedDependenciesToCopy = new HashSet(configuration.getDependencies());
        configuration.addDependency(HelperUtil.createDependency("group3", "name3", "version3"));

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
        Set<Dependency> expectedDependenciesToCopy = new HashSet(configuration.getDependencies());
        configuration.addDependency(HelperUtil.createDependency("group3", "name3", "version3"));

        Closure specClosure = HelperUtil.toClosure("{ element ->  !element.group.equals(\"group3\")}");
        Configuration copiedConfiguration = configuration.copy(specClosure);

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    private void prepareConfigurationForCopyTest() {
        configuration.setVisible(false);
        configuration.setTransitive(false);
        configuration.setDescription("descript");
        configuration.exclude(toMap("org", "value"));
        configuration.exclude(toMap("org2", "value2"));
        configuration.addArtifact(HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1"));
        configuration.addArtifact(HelperUtil.createPublishArtifact("name2", "ext2", "type2", "classifier2"));
        configuration.addDependency(HelperUtil.createDependency("group1", "name1", "version1"));
        configuration.addDependency(HelperUtil.createDependency("group2", "name2", "version2"));
    }

    private void assertThatCopiedConfigurationHasElementsAndName(Configuration copiedConfiguration, Set<Dependency> expectedDependencies) {
        assertThat(copiedConfiguration.getName(), equalTo("copyOf" + configuration.getName()));
        assertThat(copiedConfiguration.isVisible(), equalTo(configuration.isVisible()));
        assertThat(copiedConfiguration.isTransitive(), equalTo(configuration.isTransitive()));
        assertThat(copiedConfiguration.getDescription(), equalTo(configuration.getDescription()));
        assertThat(copiedConfiguration.getAllArtifacts(), equalTo(configuration.getAllArtifacts()));
        assertThat(copiedConfiguration.getExcludeRules(), equalTo(configuration.getExcludeRules()));
        assertThat(copiedConfiguration.getExcludeRules().iterator().next(), not(sameInstance(configuration.getExcludeRules().iterator().next())));
        assertThat(copiedConfiguration.getDependencies(), equalTo(expectedDependencies));
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
        Set<Dependency> expectedDependenciesToCopy = new HashSet(configuration.getAllDependencies());
        configuration.addDependency(HelperUtil.createDependency("group3", "name3", "version3"));

        Closure specClosure = HelperUtil.toClosure("{ element ->  !element.group.equals(\"group3\")}");
        Configuration copiedConfiguration = configuration.copyRecursive(specClosure);

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    @Test
    public void copyRecursiveWithClosure() {
        prepareConfigurationForCopyRecursiveTest();
        Set<Dependency> expectedDependenciesToCopy = new HashSet(configuration.getAllDependencies());
        configuration.addDependency(HelperUtil.createDependency("group3", "name3", "version3"));

        Configuration copiedConfiguration = configuration.copyRecursive(new Spec<Dependency>() {
            public boolean isSatisfiedBy(Dependency element) {
                return !element.getGroup().equals("group3");
            }
        });

        assertThatCopiedConfigurationHasElementsAndName(copiedConfiguration, expectedDependenciesToCopy);
    }

    private void prepareConfigurationForCopyRecursiveTest() {
        prepareConfigurationForCopyTest();
        Dependency similarDependency2InOtherConf = HelperUtil.createDependency("group2", "name2", "version2");
        Dependency otherConfDependency = HelperUtil.createDependency("group4", "name4", "version4");
        Configuration otherConf = configurationContainer.add("otherConf");
        otherConf.addDependency(similarDependency2InOtherConf);
        otherConf.addDependency(otherConfDependency);
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
    public void propertyChangeWithNonUnresolvedState_shouldThrowEx() {
        makeResolveReturnFileSet(new HashSet());
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
                configuration.addArtifact(context.mock(PublishArtifact.class));
            }
        });
        assertInvalidUserDataException(new Executer() {
            public void execute() {
                configuration.addDependency(context.mock(Dependency.class));
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
                configuration.removeArtifact(context.mock(PublishArtifact.class, "removeeArtifact"));
            }
        });
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



