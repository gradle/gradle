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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.CacheUsage;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.internal.dependencies.ivyservice.*;
import org.gradle.api.internal.dependencies.ivyservice.moduleconverter.DefaultModuleDescriptorConverter;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.initialization.ISettingsFinder;
import org.gradle.util.HelperUtil;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultDependencyManagerFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    private File expectedBuildResolverDir;
    private File testRootDir;
    private File testGradleUserHome;
    private ISettingsFinder settingsFinderMock;
    private Project expectedProject;

    @Before
    public void setUp() {
        expectedProject = new DefaultProject("someProject");
        testRootDir = HelperUtil.makeNewTestDir();
        expectedBuildResolverDir = new File(testRootDir, Project.TMP_DIR_NAME + "/" + DependencyManager.BUILD_RESOLVER_NAME);
        expectedBuildResolverDir.mkdirs();
        settingsFinderMock = context.mock(ISettingsFinder.class);
        testGradleUserHome = new File("testGradleUserHome");
        context.checking(new Expectations() {{
            allowing(settingsFinderMock).getSettingsDir(); will(returnValue(testRootDir));
        }});
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test public void testCreate() {
        DefaultDependencyManager dependencyManager = (DefaultDependencyManager)
                new DefaultDependencyManagerFactory(settingsFinderMock, CacheUsage.ON).createDependencyManager(expectedProject, testGradleUserHome);
        assertTrue(expectedBuildResolverDir.isDirectory());
        checkCommon(expectedProject, dependencyManager);
    }

    @Test public void testCreateWithCacheOff() {
        DefaultDependencyManager dependencyManager = (DefaultDependencyManager)
                new DefaultDependencyManagerFactory(settingsFinderMock, CacheUsage.OFF).createDependencyManager(expectedProject, testGradleUserHome);
        assertTrue(!expectedBuildResolverDir.isDirectory());
        checkCommon(expectedProject, dependencyManager);
    }

    @Test public void testCreateWithCacheRebuild() {
        DefaultDependencyManager dependencyManager = (DefaultDependencyManager)
                new DefaultDependencyManagerFactory(settingsFinderMock, CacheUsage.REBUILD).createDependencyManager(expectedProject, testGradleUserHome);
        assertTrue(!expectedBuildResolverDir.isDirectory());
        checkCommon(expectedProject, dependencyManager);
    }

    private void checkCommon(Project expectedProject, DefaultDependencyManager dependencyManager) {
        assertEquals(new File(expectedProject.getBuildDir(), DependencyManager.TMP_CACHE_DIR_NAME) ,((DefaultResolverFactory) dependencyManager.getResolverFactory()).getTmpIvyCache());
        assertEquals(expectedBuildResolverDir, dependencyManager.getBuildResolverHandler().getBuildResolverDir());
        assertSame(expectedProject, dependencyManager.getProject());
        assertNotNull(dependencyManager.getConfigurationContainer());
        checkDependencyContainer(expectedProject, dependencyManager.getConfigurationContainer(), (DefaultDependencyContainer) dependencyManager.getDependencyContainer());
        assertThat(dependencyManager.getArtifactContainer(), Matchers.instanceOf(DefaultArtifactContainer.class));
        assertSame(dependencyManager.getIvyHandler(), ((DefaultConfigurationResolverFactory) dependencyManager.getConfigurationResolverFactory()).getIvyHandler()); 
        assertSame(testGradleUserHome, ((DefaultConfigurationResolverFactory) dependencyManager.getConfigurationResolverFactory()).getGradleUserHome()); 
        assertNotNull(dependencyManager.getClasspathResolvers());
        checkIvyHandler((DefaultIvyService) dependencyManager.getIvyHandler());

    }

    private void checkDependencyContainer(Project expectedProject, ConfigurationContainer expectedConfigurationContainer, DefaultDependencyContainer dependencyContainer) {
        assertThat(dependencyContainer.getProject(), Matchers.sameInstance(expectedProject));
        assertThat(dependencyContainer.getConfigurationContainer(), Matchers.sameInstance(expectedConfigurationContainer));
        checkDependencyFactories(dependencyContainer.getDependencyFactory().getDependencyFactories());
        assertThat(dependencyContainer.getExcludeRules(), Matchers.instanceOf(DefaultExcludeRuleContainer.class));
        assertEquals(new HashMap<String, ModuleDescriptor>(), dependencyContainer.getClientModuleRegistry());
    }

    private void checkIvyHandler(DefaultIvyService ivyHandler) {
        assertThat(ivyHandler.getSettingsConverter(), Matchers.instanceOf(DefaultSettingsConverter.class));
        assertThat(ivyHandler.getModuleDescriptorConverter(), Matchers.instanceOf(DefaultModuleDescriptorConverter.class));
        assertThat(ivyHandler.getIvyFactory(), Matchers.instanceOf(DefaultIvyFactory.class));
        assertThat(ivyHandler.getSettingsConverter(), Matchers.instanceOf(DefaultSettingsConverter.class));
        assertThat(ivyHandler.getDependencyResolver(), Matchers.instanceOf(DefaultIvyDependencyResolver.class));
        assertThat(ivyHandler.getDependencyPublisher(), Matchers.instanceOf(DefaultIvyDependencyPublisher.class));
        assertEquals(expectedBuildResolverDir, ivyHandler.getBuildResolverHandler().getBuildResolverDir());
    }

    private void checkDependencyFactories(Set<IDependencyImplementationFactory> dependencyImplementationFactories) {
        assertThat(dependencyImplementationFactories.size(), equalTo(2));
        boolean containsModule = false;
        boolean containsProject = false;
        for (IDependencyImplementationFactory dependencyImplementationFactory : dependencyImplementationFactories) {
            if (dependencyImplementationFactory instanceof ProjectDependencyFactory) {
                containsProject = true;
            } else if (dependencyImplementationFactory instanceof ModuleDependencyFactory) {
                containsModule = true;
            }
        }
        assertTrue(containsModule);
        assertTrue(containsProject);
    }

}
