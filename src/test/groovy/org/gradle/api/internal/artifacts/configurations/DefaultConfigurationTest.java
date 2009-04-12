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

import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.specs.Spec;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.toSet;
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
    private ResolverProvider resolverProvider;
    private DependencyMetaDataProvider dependencyMetaDataProvider = context.mock(DependencyMetaDataProvider.class);
    private DefaultConfigurationContainer configurationContainer;

    private DefaultConfiguration configuration;

    @Before
    public void setUp() {
        resolverProvider = createResolverProvider();
        dependencyMetaDataProvider = createDependencyMetaDataProvider();
        configurationContainer = new DefaultConfigurationContainer(ivyServiceStub, resolverProvider,
                dependencyMetaDataProvider);
        configuration = (DefaultConfiguration) configurationContainer.add(CONF_NAME);
    }

    private DependencyMetaDataProvider createDependencyMetaDataProvider() {
        return new DependencyMetaDataProvider() {
            private Map clientModuleRegistry = WrapUtil.toMap("key", "value");
            private File gradleUserHomeDir = new File("somePath");
            private InternalRepository internalRepositoryDummy = context.mock(InternalRepository.class);
            private Module module = new DefaultModule("group", "name", "version");
            public Map getClientModuleRegistry() {
                return clientModuleRegistry;
            }

            public File getGradleUserHomeDir() {
                return gradleUserHomeDir;
            }

            public InternalRepository getInternalRepository() {
                return internalRepositoryDummy;
            }

            public Module getModule() {
                return module;
            }
        };
    }

    private ResolverProvider createResolverProvider() {
        return new ResolverProvider() {
            private List<DependencyResolver> resolvers = WrapUtil.toList(context.mock(DependencyResolver.class, "resolverProvider"));
            public List<DependencyResolver> getResolvers() {
                return resolvers;
            }
        };
    }

    @Test
    public void defaultValues() {
        DefaultConfiguration configuration = new DefaultConfiguration(CONF_NAME, configurationContainer, ivyServiceStub,
                resolverProvider, dependencyMetaDataProvider);
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
        Map<String, String> excludeArgs1 = WrapUtil.toMap("org", "value");
        Map<String, String> excludeArgs2 = WrapUtil.toMap("org2", "value2");
        assertThat(configuration.exclude(excludeArgs1), sameInstance(configuration));
        configuration.exclude(excludeArgs2);
        assertThat(configuration.getExcludeRules(), equalTo(WrapUtil.<ExcludeRule>toSet(
                new DefaultExcludeRule(excludeArgs1), new DefaultExcludeRule(excludeArgs2))));
    }

    @Test
    public void setExclude() {
        Set<ExcludeRule> excludeRules = WrapUtil.<ExcludeRule>toSet(new DefaultExcludeRule(WrapUtil.toMap("org", "value")));
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
        
        configuration.setExtendsFrom(WrapUtil.toSet(configuration1));
        assertThat(configuration.getExtendsFrom(), equalTo(toSet(configuration1)));

        Configuration configuration2 = configurationContainer.add("otherConf2");
        configuration.setExtendsFrom(WrapUtil.toSet(configuration2));
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
        configuration.setExtendsFrom(WrapUtil.toSet(otherConfiguration));
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
        assertThat(Configurations.getNames(configuration.getAll()), equalTo(WrapUtil.toSet(CONF_NAME, testConf1, testConf2)));
    }
    
    @Test
    public void getAsPath() {
        File file1 = new File("somePath1");
        File file2 = new File("somePath2");
        final Set<File> fileSet = WrapUtil.toLinkedSet(file1, file2);
        makeResolveReturnFileSet(fileSet);
        assertThat(configuration.getAsPath(), equalTo(file1.getAbsolutePath() + System.getProperty("path.separator") + file2.getAbsolutePath()));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test
    public void getAsPathWithEmptyPath() {
        makeResolveReturnFileSet(new HashSet());
        assertThat(configuration.getAsPath(), equalTo(""));
    }

    @Test(expected = InvalidUserDataException.class)
    public void getAsPathWithFailure_shouldThrowInvalidUserDataEx() {
        prepareForResolveWithErrors();
        configuration.resolve();
    }

    @Test
    public void resolve() {
        final Set<File> fileSet = WrapUtil.toSet(new File("somePath"));
        makeResolveReturnFileSet(fileSet);
        assertThat(configuration.resolve(), equalTo(fileSet));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    @Test(expected = InvalidUserDataException.class)
    public void resolveWithFailure_shouldThrowInvalidUserDataEx() {
        prepareForResolveWithErrors();
        configuration.resolve();
    }

    private void prepareForResolveWithErrors() {
        final ResolveReport resolveReport = context.mock(ResolveReport.class);
        final Set<File> fileSet = WrapUtil.toSet(new File("somePath"));
        prepareResolveAsReport(resolveReport, true);
    }

    private void makeResolveReturnFileSet(final Set<File> fileSet) {
        final ResolveReport resolveReport = context.mock(ResolveReport.class);
        context.checking(new Expectations() {{
            prepareResolveAsReport(resolveReport, false);
            allowing(ivyServiceStub).resolveFromReport(configuration, resolveReport);
            will(returnValue(fileSet));
        }});
    }

    @Test
    public void resolveSuccessfullyAsReport() {
        final ResolveReport resolveReport = context.mock(ResolveReport.class);
        prepareResolveAsReport(resolveReport, false);
        assertThat(configuration.resolveAsReport(), equalTo(resolveReport));
        assertThat(configuration.getState(), equalTo(Configuration.State.RESOLVED));
    }

    private void prepareResolveAsReport(final ResolveReport resolveReport, final boolean withErrors) {
        context.checking(new Expectations() {{
            allowing(ivyServiceStub).resolveAsReport(configuration, dependencyMetaDataProvider.getModule(),
                    dependencyMetaDataProvider.getGradleUserHomeDir(), dependencyMetaDataProvider.getClientModuleRegistry());
            will(returnValue(resolveReport));
            allowing(resolveReport).hasError();
            will(returnValue(withErrors));
        }});
    }

    @Test
    public void multipleResolvesShouldUseCachedResult() {
        final ResolveReport resolveReport = context.mock(ResolveReport.class);
        context.checking(new Expectations() {{
            one(ivyServiceStub).resolveAsReport(configuration, dependencyMetaDataProvider.getModule(),
                    dependencyMetaDataProvider.getGradleUserHomeDir(), dependencyMetaDataProvider.getClientModuleRegistry());
            will(returnValue(resolveReport));
            allowing(resolveReport).hasError();
            will(returnValue(false));
        }});
        assertThat(configuration.resolveAsReport(), sameInstance(configuration.resolveAsReport()));
    }

    @Test
    public void publish() {
        final Configuration otherConfiguration = configurationContainer.add("testConf").extendsFrom(configuration);
        final PublishInstruction publishInstruction = createAnonymousPublishInstruction();
        final List<DependencyResolver> dependencyResolvers = WrapUtil.toList(context.mock(DependencyResolver.class, "publish"));
        context.checking(new Expectations() {{
            allowing(ivyServiceStub).publish(new LinkedHashSet(otherConfiguration.getHierarchy()), publishInstruction, dependencyResolvers, dependencyMetaDataProvider.getModule(),
                    dependencyMetaDataProvider.getGradleUserHomeDir());
        }});
        otherConfiguration.publish(dependencyResolvers, publishInstruction);
    }

    @Test
    public void getDependencyResolver() {
        assertThat(configuration.getDependencyResolvers(), equalTo(resolverProvider.getResolvers()));    
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
        return new DefaultConfiguration(confName, configurationContainer, ivyServiceStub, resolverProvider, dependencyMetaDataProvider);
    }

//    @Ignore
////    public void buildArtifacts() {
////        final TaskDependency taskDependencyMock = context.mock(TaskDependency.class);
////        final Task taskMock = context.mock(Task.class);
////        context.checking(new Expectations() {{
////            allowing(artifactContainerMock).getArtifacts(confs(TEST_CONF_NAME));
////            will(returnValue(WrapUtil.toSet(publishArtifactMock)));
////
////            allowing(publishArtifactMock).getTaskDependency();
////            will(returnValue(taskDependencyMock));
////
////            allowing(taskDependencyMock).getDependencies(with(any(Task.class)));
////            will(returnValue(WrapUtil.toSet(taskMock)));
////        }});
////        assertThat((Set<Task>) configurationResolver.getBuildArtifacts().getDependencies(taskMock),
////                equalTo(WrapUtil.toSet(taskMock)));
////    }
//
//    @Test
//    public void buildProjectDependencies() {
//        Configuration otherConfiguration = context.mock(Configuration.class);
//        configuration.setExtendsFrom(otherConfiguration.getName());
//
//        final TaskDependency taskDependencyMock = context.mock(TaskDependency.class);
//        final Task taskMock = context.mock(Task.class);
//        context.checking(new Expectations() {{
//            allowing().getDependencies(and(confs(TEST_CONF_NAME), type(Type.PROJECT)));
//            will(returnValue(WrapUtil.toSet(projectDependencyMock)));
//
//            allowing(projectDependencyMock).getTaskDependency();
//            will(returnValue(taskDependencyMock));
//
//            allowing(taskDependencyMock).getDependencies(with(any(Task.class)));
//            will(returnValue(WrapUtil.toSet(taskMock)));
//        }});
//        assertThat((Set<Task>) configurationResolver.getBuildArtifacts().getDependencies(taskMock),
//                equalTo(WrapUtil.toSet(taskMock)));
//    }

    @Test
    public void getDependencies() {
        Dependency dependency = context.mock(Dependency.class);
        configuration.addDependency(dependency);
        assertThat(configuration.getDependencies(), equalTo(WrapUtil.toSet(dependency)));
    }

    @Test
    public void getProjectDependencies() {
        ProjectDependency projectDependency = context.mock(ProjectDependency.class);
        configuration.addDependency(context.mock(Dependency.class));
        configuration.addDependency(projectDependency);
        assertThat(configuration.getProjectDependencies(), equalTo(WrapUtil.toSet(projectDependency)));
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

        assertThat(configuration.getAllDependencies(), equalTo(WrapUtil.toSet(dependencyConf, dependencyOtherConf2)));
        assertCorrectInstanceInAllDependencies(configuration.getAllDependencies(), dependencyConf);
    }

    @Test
    public void getAllProjectDependencies() {
        ProjectDependency projectDependencyCurrentConf = context.mock(ProjectDependency.class, "projectDepCurrentConf");
        configuration.addDependency(context.mock(Dependency.class, "depCurrentConf"));
        configuration.addDependency(projectDependencyCurrentConf);
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.extendsFrom(otherConf);
        ProjectDependency projectDependencyExtendedConf = context.mock(ProjectDependency.class, "projectDepExtendedConf");
        otherConf.addDependency(context.mock(Dependency.class, "depExtendedConf"));
        otherConf.addDependency(projectDependencyExtendedConf);

        assertThat(configuration.getAllProjectDependencies(), equalTo(WrapUtil.toSet(projectDependencyCurrentConf, projectDependencyExtendedConf)));
    }

    @Test
    public void getAllArtifacts() {
        PublishArtifact artifactConf = HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1");
        PublishArtifact artifactOtherConf1 = HelperUtil.createPublishArtifact("name1", "ext1", "type1", "classifier1");
        PublishArtifact artifactOtherConf2 = HelperUtil.createPublishArtifact("name2", "ext2", "type2", "classifier2");
        Configuration otherConf = configurationContainer.add("otherConf");
        configuration.addArtifact(artifactConf);
        configuration.extendsFrom(otherConf);
        otherConf.addArtifact(artifactOtherConf1);
        otherConf.addArtifact(artifactOtherConf2);
        assertThat(configuration.getAllArtifacts(), equalTo(WrapUtil.toSet(artifactConf, artifactOtherConf2)));
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

    private void prepareConfigurationForCopyTest() {
        configuration.addDependency(HelperUtil.createDependency("group1", "name1", "version1"));
        configuration.addDependency(HelperUtil.createDependency("group2", "name2", "version2"));
    }

    private void assertThatCopiedConfigurationHasElementsAndName(Configuration copiedConfiguration, Set<Dependency> expectedDependencies) {
        assertThat(copiedConfiguration.getName(), equalTo("copyOf" + configuration.getName()));
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
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.setTransitive(true); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.setDescription("someDesc"); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.setExcludeRules(new HashSet<ExcludeRule>()); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.setExtendsFrom(new HashSet<Configuration>()); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.setVisible(true); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.addArtifact(context.mock(PublishArtifact.class)); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.addDependency(context.mock(Dependency.class)); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.exclude(new HashMap<String, String>()); } });
        assertInvalidUserDataException(new Executer() { public void execute() { configuration.extendsFrom(context.mock(Configuration.class)); } });
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



