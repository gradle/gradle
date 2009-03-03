/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import groovy.lang.Closure;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.ivyservice.BuildResolverHandler;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class BaseDependencyManagerTest {
    protected static final String TEST_CONF1 = "conf1";

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);    
    }};
    protected Project project = HelperUtil.createRootProject();
    protected DependencyContainerInternal dependencyContainerMock = context.mock(DependencyContainerInternal.class);
    protected ArtifactContainer artifactContainerMock = context.mock(ArtifactContainer.class);
    protected ConfigurationContainer configurationContainerMock = context.mock(ConfigurationContainer.class);
    protected ConfigurationResolverFactory configurationResolverFactoryMock = context.mock(ConfigurationResolverFactory.class);
    protected ResolverContainer dependencyResolversMock = context.mock(ResolverContainer.class);
    protected ResolverFactory resolverFactoryMock = context.mock(ResolverFactory.class);
    protected BuildResolverHandler buildResolverHandler = context.mock(BuildResolverHandler.class);

    protected IvyService ivyServiceMock = context.mock(IvyService.class);
    private BaseDependencyManager dependencyManager = new BaseDependencyManager(
            project, dependencyContainerMock, artifactContainerMock, configurationContainerMock,
            configurationResolverFactoryMock, dependencyResolversMock, resolverFactoryMock, buildResolverHandler, ivyServiceMock
    );
    private Dependency testDependency1 = context.mock(Dependency.class, "dep1");
    private Dependency testDependency2 = context.mock(Dependency.class, "dep2");
    private Dependency[] testDependencies = WrapUtil.toArray(testDependency1, testDependency2);

    private List<Dependency> testDependencyList = Arrays.asList(testDependencies);
    private List<String> testConfigurations = WrapUtil.toList(TEST_CONF1, "conf2");
    protected ConfigurationResolver testConfigurationResolver = context.mock(ConfigurationResolver.class);
    private Configuration testConfiguration = context.mock(Configuration.class);
    private Map<Configuration, List<String>> testConfigurationMapping = WrapUtil.toMap(
            testConfiguration,
            WrapUtil.toList("depConf1", "depConf2"));

    protected BaseDependencyManager getDependencyManager() {
        return dependencyManager;
    }
    
    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(dependencyContainerMock).getDependencies();
            will(returnValue(testDependencyList));

            allowing(dependencyContainerMock).getDependencies(HelperUtil.TEST_SEPC);
            will(returnValue(testDependencyList));
        }});   
    }

    @Test
    public void init() {
        assertThat((DefaultConf2ScopeMappingContainer) getDependencyManager().getDefaultMavenScopeMapping(),
                equalTo(new DefaultConf2ScopeMappingContainer()));
    }

    @Test
    public void getDependencies() {
        assertThat((List<Dependency>) getDependencyManager().getDependencies(),
                equalTo(testDependencyList));
    }

    @Test
    public void getDependenciesWithFilter() {
        assertThat((List<Dependency>) getDependencyManager().getDependencies(HelperUtil.TEST_SEPC),
                equalTo(testDependencyList));
    }

    @Test
    public void testAddDependencies() {
        context.checking(new Expectations() {{
            one(dependencyContainerMock).addDependencies(testDependencies);
        }});
        getDependencyManager().addDependencies(testDependencies);
    }

    @Test
    public void testDependenciesWithConfs() {
        context.checking(new Expectations() {{
            one(dependencyContainerMock).dependencies(testConfigurations, testDependencies);
        }});
        getDependencyManager().dependencies(testConfigurations, testDependencies);
    }

    @Test
    public void testDependenciesWithConfMappings() {
        context.checking(new Expectations() {{
            one(dependencyContainerMock).dependencies(testConfigurationMapping, testDependencies);
        }});
        getDependencyManager().dependencies(testConfigurationMapping, testDependencies);
    }

    @Test
    public void testDependencyWithConfs() {
        context.checking(new Expectations() {{
            one(dependencyContainerMock).dependency(testConfigurations, testDependencies, HelperUtil.TEST_CLOSURE);
        }});
        getDependencyManager().dependency(testConfigurations, testDependencies, HelperUtil.TEST_CLOSURE);
    }

    @Test
    public void testDependencyWithConfMappings() {
        context.checking(new Expectations() {{
            one(dependencyContainerMock).dependency(testConfigurationMapping, testDependencies, HelperUtil.TEST_CLOSURE);
        }});
        getDependencyManager().dependency(testConfigurationMapping, testDependencies, HelperUtil.TEST_CLOSURE);
    }

    @Test
    public void testClientModuleWithConfs() {
        final String testModuleDescriptor = "someDesc";
        context.checking(new Expectations() {{
            one(dependencyContainerMock).clientModule(testConfigurations, testModuleDescriptor);
        }});
        getDependencyManager().clientModule(testConfigurations, testModuleDescriptor);
    }

    @Test
    public void testClientModuleWithConfsAndClosure() {
        final String testModuleDescriptor = "someDesc";
        context.checking(new Expectations() {{
            one(dependencyContainerMock).clientModule(testConfigurations, testModuleDescriptor, HelperUtil.TEST_CLOSURE);
        }});
        getDependencyManager().clientModule(testConfigurations, testModuleDescriptor, HelperUtil.TEST_CLOSURE);
    }

    @Test
    public void testClientModuleWithConfMappings() {
        final String testModuleDescriptor = "someDesc";
        context.checking(new Expectations() {{
            one(dependencyContainerMock).clientModule(testConfigurationMapping, testModuleDescriptor);
        }});
        getDependencyManager().clientModule(testConfigurationMapping, testModuleDescriptor);
    }

    @Test
    public void testClientModuleWithConfMappingsAndClosure() {
        final String testModuleDescriptor = "someDesc";
        context.checking(new Expectations() {{
            one(dependencyContainerMock).clientModule(testConfigurationMapping, testModuleDescriptor, HelperUtil.TEST_CLOSURE);
        }});
        getDependencyManager().clientModule(testConfigurationMapping, testModuleDescriptor, HelperUtil.TEST_CLOSURE);
    }

    @Test
    public void testGetExcludeRules() {
        final ExcludeRuleContainer testExcludeRules = context.mock(ExcludeRuleContainer.class);
        context.checking(new Expectations() {{
            allowing(dependencyContainerMock).getExcludeRules();
            will(returnValue(testExcludeRules));
        }});
        assertThat(getDependencyManager().getExcludeRules(), sameInstance(testExcludeRules));
    }

    @Test
    public void testAddConfiguration() {
        configureConfigurationResolverFactory();
        context.checking(new Expectations() {{
            one(configurationContainerMock).add(TEST_CONF1, null);
            will(returnValue(testConfiguration));
        }});
        assertThat(getDependencyManager().addConfiguration(TEST_CONF1), sameInstance(testConfigurationResolver));
    }

    @Test
    public void testAddConfigurationWithClosure() {
        configureConfigurationResolverFactory();
        context.checking(new Expectations() {{
            one(configurationContainerMock).add(TEST_CONF1, HelperUtil.TEST_CLOSURE);
            will(returnValue(testConfiguration));
        }});
        assertThat(getDependencyManager().addConfiguration(TEST_CONF1, HelperUtil.TEST_CLOSURE), sameInstance(testConfigurationResolver));
    }

    private void configureConfigurationResolverFactory() {
        context.checking(new Expectations() {{
            allowing(configurationResolverFactoryMock).createConfigurationResolver(
                testConfiguration, dependencyContainerMock, dependencyResolversMock, artifactContainerMock, configurationContainerMock);
            will(returnValue(testConfigurationResolver));
        }});
    }

    @Test
    public void testFindConfiguration() {
        configureConfigurationResolverFactory();
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).find(TEST_CONF1);
            will(returnValue(testConfiguration));
        }});
        assertThat(getDependencyManager().findConfiguration(TEST_CONF1), sameInstance(testConfigurationResolver));
    }

    @Test
    public void testFindNonExisitingConfiguration() {
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).find(TEST_CONF1);
            will(returnValue(null));
        }});
        assertThat(getDependencyManager().findConfiguration(TEST_CONF1), equalTo(null));
    }

    @Test
    public void testConfiguration() {
        configureConfigurationResolverFactory();
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).get(TEST_CONF1, null);
            will(returnValue(testConfiguration));
        }});
        assertThat(getDependencyManager().configuration(TEST_CONF1), sameInstance(testConfigurationResolver));
    }

    @Test
    public void testConfigurationWithClosure() {
        configureConfigurationResolverFactory();
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).get(TEST_CONF1, HelperUtil.TEST_CLOSURE);
            will(returnValue(testConfiguration));
        }});
        assertThat(getDependencyManager().configuration(TEST_CONF1, HelperUtil.TEST_CLOSURE), sameInstance(testConfigurationResolver));
    }

    @Test
    public void testGetConfigurations() {
        configureConfigurationResolverFactory();
        context.checking(new Expectations() {{
            allowing(configurationContainerMock).get();
            will(returnValue(WrapUtil.toSet(testConfiguration)));
        }});
        assertThat(getDependencyManager().getConfigurations(), Matchers.equalTo(WrapUtil.<ConfigurationResolver>toList(testConfigurationResolver)));
    }

    @Test
    public void testGetIvy() throws IOException {
        final Ivy testIvy = configureGetIvy(new ArrayList<DependencyResolver>());
        assertThat(getDependencyManager().getIvy(), sameInstance(testIvy));
    }

    @Test
    public void testIvy() throws IOException {
        List<DependencyResolver> testPublishResolvers = WrapUtil.toList(context.mock(DependencyResolver.class, "publish")); 
        final Ivy testIvy = configureGetIvy(testPublishResolvers);
        assertThat(getDependencyManager().ivy(testPublishResolvers), sameInstance(testIvy));
    }

    private Ivy configureGetIvy(final List<DependencyResolver> publishResolvers) throws IOException {
        final Ivy testIvy = new Ivy();
        final List<DependencyResolver> testResolvers = WrapUtil.toList(context.mock(DependencyResolver.class));
        final Map<String, ModuleDescriptor> testClientModuleRegistry = WrapUtil.toMap("key", context.mock(ModuleDescriptor.class));
        final File testGradleUserHome = new File("testGradleUserHome").getCanonicalFile();
        project.getBuild().getStartParameter().setGradleUserHomeDir(testGradleUserHome);

        context.checking(new Expectations() {{
            allowing(dependencyContainerMock).getClientModuleRegistry();
            will(returnValue(testClientModuleRegistry));

            allowing(dependencyResolversMock).getResolverList();
            will(returnValue(testResolvers));
            
            allowing(ivyServiceMock).ivy(testResolvers, publishResolvers, testGradleUserHome, testClientModuleRegistry);
            will(returnValue(testIvy));
        }});
        return testIvy;
    }

    @Test
    public void testGetBuildResolver() {
        final RepositoryResolver testBuildResolver = context.mock(RepositoryResolver.class);
        context.checking(new Expectations() {{
            allowing(buildResolverHandler).getBuildResolver();
            will(returnValue(testBuildResolver));
        }});
        assertThat(getDependencyManager().getBuildResolver(), sameInstance(testBuildResolver));
    }

    @Test
    public void testGetBuildResolverDir() {
        final File testBuildResolverDir = new File("buildResolverDir");
        context.checking(new Expectations() {{
            allowing(buildResolverHandler).getBuildResolverDir();
            will(returnValue(testBuildResolverDir));
        }});
        assertThat(getDependencyManager().getBuildResolverDir(), equalTo(testBuildResolverDir));
    }

    @Test
    public void testAddFlatDirResolver() {
        final FileSystemResolver testFlatDirResolver = new FileSystemResolver();
        final Object[] testDirs = WrapUtil.toArray(new Object());
        final String testName = "name";
        context.checking(new Expectations() {{
            allowing(dependencyResolversMock).createFlatDirResolver(testName, testDirs);
            will(returnValue(testFlatDirResolver));

            one(dependencyResolversMock).add(testFlatDirResolver);
            will(returnValue(testFlatDirResolver));
        }});
        assertThat(getDependencyManager().addFlatDirResolver(testName, testDirs), sameInstance(testFlatDirResolver));
    }

    @Test
    public void testAddMavenRepo() {
        final AbstractResolver testDependencyResolver = context.mock(AbstractResolver.class);
        final String[] testJarRepoUrls = WrapUtil.toArray("a", "b");
        configureForAddMavenRepo(DependencyManager.DEFAULT_MAVEN_REPO_NAME, DependencyManager.MAVEN_REPO_URL, testJarRepoUrls, testDependencyResolver);
        assertThat((AbstractResolver) getDependencyManager().addMavenRepo(testJarRepoUrls), sameInstance(testDependencyResolver));
    }

    @Test
    public void testAddMavenStyleRepo() {
        final String testName = "name";
        final String testRoot = "testRoot";
        final String[] testJarRepoUrls = WrapUtil.toArray("a", "b");
        final AbstractResolver testDependencyResolver = context.mock(AbstractResolver.class);
        configureForAddMavenRepo(testName, testRoot, testJarRepoUrls, testDependencyResolver);

        assertThat((AbstractResolver) getDependencyManager().addMavenStyleRepo(testName, testRoot, testJarRepoUrls), sameInstance(testDependencyResolver));
    }

    private void configureForAddMavenRepo(final String testName, final String testRoot, final String[] testJarRepoUrls, final DependencyResolver testDependencyResolver) {
        context.checking(new Expectations() {{
            allowing(dependencyResolversMock).createMavenRepoResolver(testName,
                    testRoot, testJarRepoUrls);
            will(returnValue(testDependencyResolver));

            one(dependencyResolversMock).add(testDependencyResolver);
            will(returnValue(testDependencyResolver));
        }});
    }

    @Test
    public void testGetClasspathResolvers() {
        assertThat(getDependencyManager().getClasspathResolvers(), sameInstance(dependencyResolversMock));
    }
    
    @Test
    public void testAddIvySettingsTransformer() {
        final Transformer<IvySettings> transformer = context.mock(Transformer.class);
        final Closure transformerClosure = HelperUtil.TEST_CLOSURE;
        final SettingsConverter settingsConverterMock = context.mock(SettingsConverter.class);
        context.checking(new Expectations() {{
            allowing(ivyServiceMock).getSettingsConverter();
            will(returnValue(settingsConverterMock));
            
            one(settingsConverterMock).addIvyTransformer(transformer);
            one(settingsConverterMock).addIvyTransformer(transformerClosure);
        }});
        getDependencyManager().addIvySettingsTransformer(transformer);
        getDependencyManager().addIvySettingsTransformer(HelperUtil.TEST_CLOSURE);
    }

    @Test
    public void testAddIvyModuleTransformer() {
        final ModuleDescriptorConverter moduleDescriptorConverterMock = context.mock(ModuleDescriptorConverter.class);
        final Transformer<DefaultModuleDescriptor> transformer = context.mock(Transformer.class);
        final Closure transformerClosure = HelperUtil.TEST_CLOSURE;
        context.checking(new Expectations() {{
            allowing(ivyServiceMock).getModuleDescriptorConverter();
            will(returnValue(moduleDescriptorConverterMock));

            one(moduleDescriptorConverterMock).addIvyTransformer(transformer);
            one(moduleDescriptorConverterMock).addIvyTransformer(transformerClosure);
        }});
        getDependencyManager().addIvyModuleTransformer(transformer);
        getDependencyManager().addIvyModuleTransformer(HelperUtil.TEST_CLOSURE);
    }

    @Test
    public void testGetIvyHandler() {
        assertThat(getDependencyManager().getIvyHandler(), sameInstance(ivyServiceMock));
    }

    @Test
    public void testCreateModuleDescriptor() {
        final ModuleDescriptorConverter moduleDescriptorConverterMock = context.mock(ModuleDescriptorConverter.class);
        final ModuleDescriptor testModuleDescriptor = context.mock(ModuleDescriptor.class);
        context.checking(new Expectations() {{
            allowing(ivyServiceMock).getModuleDescriptorConverter();
            will(returnValue(moduleDescriptorConverterMock));

            allowing(moduleDescriptorConverterMock).convert(new HashMap<String, Boolean>(), configurationContainerMock, HelperUtil.TEST_SEPC,
                    dependencyContainerMock, HelperUtil.TEST_SEPC, artifactContainerMock, HelperUtil.TEST_SEPC);
            will(returnValue(testModuleDescriptor));
        }});
        assertThat(getDependencyManager().createModuleDescriptor(HelperUtil.TEST_SEPC, HelperUtil.TEST_SEPC, HelperUtil.TEST_SEPC),
                sameInstance(testModuleDescriptor));
    }

    @Test
    public void testCreateResolverContainer() {
        ResolverContainer resolverContainer = getDependencyManager().createResolverContainer();
        assertThat(resolverContainer.getResolverList().size(), equalTo(0));
    }

    @Test
    public void testGetArtifacts() {
        final Set<PublishArtifact> testArtifacts = WrapUtil.toSet(context.mock(PublishArtifact.class));
        context.checking(new Expectations() {{
            allowing(artifactContainerMock).getArtifacts();
            will(returnValue(testArtifacts));
        }});
        assertThat(getDependencyManager().getArtifacts(), equalTo(testArtifacts));
    }

    @Test
    public void testAddArtifacts() {
        final PublishArtifact[] testArtifacts = WrapUtil.toArray(context.mock(PublishArtifact.class));
        context.checking(new Expectations() {{
            one(artifactContainerMock).addArtifacts(testArtifacts);
        }});
        getDependencyManager().addArtifacts(testArtifacts);
    }
}
