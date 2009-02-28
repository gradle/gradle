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

package org.gradle.initialization

import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.StartParameter
import org.gradle.api.DependencyManager
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.ConfigurationResolver
import org.gradle.api.artifacts.ConfigurationResolvers
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.artifacts.DependencyManagerFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.JUnit4GroovyMockery
import org.hamcrest.Matchers
import org.jmock.integration.junit4.JMock
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.initialization.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class DefaultSettingsTest {
    File settingsDir
    StartParameter startParameter
    Map gradleProperties
    DependencyManager dependencyManagerMock
    BuildSourceBuilder buildSourceBuilderMock
    ScriptSource scriptSourceMock
    DefaultSettings settings
    DependencyManagerFactory dependencyManagerFactoryMock
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    DefaultProjectDescriptorRegistry projectDescriptorRegistry;

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        settingsDir = new File('/somepath/root').absoluteFile
        gradleProperties = [someGradleProp: 'someValue']
        startParameter = new StartParameter(currentDir: new File(settingsDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'), buildFileName: 'root.gradle')
        dependencyManagerMock = context.mock(DependencyManager)
        buildSourceBuilderMock = context.mock(BuildSourceBuilder)
        dependencyManagerFactoryMock = context.mock(DependencyManagerFactory)
        scriptSourceMock = context.mock(ScriptSource)

        projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
        context.checking {
            one(dependencyManagerFactoryMock).createDependencyManager(withParam(BuildDependenciesProjectMatcher.equalsBuildProject(startParameter)),
                withParam(Matchers.equalTo(startParameter.gradleUserHomeDir)))
            will(returnValue(dependencyManagerMock))
            one(dependencyManagerMock).addConfiguration("build")
        }
        settings = new DefaultSettings(dependencyManagerFactoryMock, projectDescriptorRegistry, buildSourceBuilderMock, settingsDir, scriptSourceMock, startParameter)
    }

    @Test public void testSettings() {
        assert settings.startParameter.is(startParameter)
        assertEquals(settingsDir, settings.getSettingsDir())
        assert settings.dependencyManager.is(dependencyManagerMock)

        assert settings.buildSourceBuilder.is(buildSourceBuilderMock)
        assertEquals(Project.DEFAULT_BUILD_FILE, settings.buildSrcStartParameter.buildFileName)
        assertEquals([JavaPlugin.CLEAN, ConfigurationResolvers.uploadInternalTaskName(Dependency.MASTER_CONFIGURATION)],
                settings.buildSrcStartParameter.taskNames)
        assertTrue(settings.buildSrcStartParameter.searchUpwards)
        assertNull(settings.getRootProject().getParent())
        assertEquals(settingsDir, settings.getRootProject().getProjectDir())
        assertEquals(settings.getRootProject().getProjectDir().getName(), settings.getRootProject().getName())
        assertEquals(settings.rootProject.buildFileName, startParameter.buildFileName);
    }

    @Test public void testInclude() {
        ProjectDescriptor rootProjectDescriptor = settings.getRootProject();
        String projectA = "a"
        String projectB = "b"
        String projectC = "c"
        String projectD = "d"
        settings.include([projectA, "$projectB:$projectC"] as String[])

        assertEquals(2, rootProjectDescriptor.getChildren().size())
        testDescriptor(settings.project(":$projectA"), projectA, new File(settingsDir, projectA))
        testDescriptor(settings.project(":$projectB"), projectB, new File(settingsDir, projectB))

        assertEquals(1, settings.project(":$projectB").getChildren().size())
        testDescriptor(settings.project(":$projectB:$projectC"), projectC, new File(settingsDir, "$projectB/$projectC"))
    }

    @Test public void testIncludeFlat() {
        ProjectDescriptor rootProjectDescriptor = settings.getRootProject();
        String projectA = "a"
        String projectB = "b"
        String[] paths = [projectA, projectB]
        settings.includeFlat(paths)
        assertEquals(2, rootProjectDescriptor.getChildren().size())
        testDescriptor(settings.project(":" + projectA), projectA, new File(settingsDir.parentFile, projectA))
        testDescriptor(settings.project(":" + projectB), projectB, new File(settingsDir.parentFile, projectB))
    }

    private void testDescriptor(DefaultProjectDescriptor descriptor, String name, File projectDir) {
        assertEquals(name, descriptor.getName(), descriptor.getName())
        assertEquals(projectDir, descriptor.getProjectDir())
    }

    @Test public void testCreateProjectDescriptor() {
        String testName = "testname"
        File testDir = new File("testDir")
        DefaultProjectDescriptor projectDescriptor = settings.createProjectDescriptor(settings.getRootProject(), testName, testDir)
        assertSame(settings.getRootProject(), projectDescriptor.getParent())
        assertSame(settings.getProjectDescriptorRegistry(), projectDescriptor.getProjectDescriptorRegistry())
        assertEquals(testName, projectDescriptor.getName())
        assertEquals(testDir, projectDescriptor.getProjectDir())
    }

    @Test public void testFindDescriptorByPath() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor();
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.getPath())
        assertSame(foundProjectDescriptor, projectDescriptor)
    }

    @Test public void testFindDescriptorByProjectDir() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.getProjectDir())
        assertSame(foundProjectDescriptor, projectDescriptor)
    }

    @Test (expected = UnknownProjectException) public void testDescriptorByPath() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.getPath())
        assertSame(foundProjectDescriptor, projectDescriptor)
        settings.project("unknownPath")
    }


    @Test (expected = UnknownProjectException) public void testDescriptorByProjectDir() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.getProjectDir())
        assertSame(foundProjectDescriptor, projectDescriptor)
        settings.project(new File("unknownPath"))
    }

    private DefaultProjectDescriptor createTestDescriptor() {
        String testName = "testname"
        File testDir = new File("testDir")
        return settings.createProjectDescriptor(settings.getRootProject(), testName, testDir)
    }


    @Test public void testDependencies() {
        String[] expectedDependencies = ["dep1", "dep2"]
        context.checking {
            one(dependencyManagerMock).dependencies([DefaultSettings.BUILD_CONFIGURATION], expectedDependencies as Object[])
        }
        settings.dependencies(expectedDependencies)
    }

    @Test public void testDependency() {
        String expectedId = "dep1"
        Closure expectedConfigureClosure
        context.checking {
            one(dependencyManagerMock).dependency([DefaultSettings.BUILD_CONFIGURATION], expectedId, expectedConfigureClosure)
        }
        settings.dependency(expectedId, expectedConfigureClosure)
    }

    @Test public void testClientModule() {
        String expectedId = "dep1"
        Closure expectedConfigureClosure
        context.checking {
            one(dependencyManagerMock).clientModule([DefaultSettings.BUILD_CONFIGURATION], expectedId, expectedConfigureClosure)
        }
        settings.clientModule(expectedId, expectedConfigureClosure)
    }

    @Test public void testAddMavenRepo() {
        DualResolver expectedResolver = new DualResolver()
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        context.checking {
            one(dependencyManagerMock).addMavenRepo(expectedJarRepoUrls); will(returnValue(expectedResolver))
        }
        assert settings.addMavenRepo(expectedJarRepoUrls).is(expectedResolver)
    }

    @Test public void testAddMavenStyleRepo() {
        DualResolver expectedResolver = new DualResolver()
        String expectedName = 'somename'
        String expectedRoot = 'http://www.root.org'
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        context.checking {
            one(dependencyManagerMock).addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls);
            will(returnValue(expectedResolver))
        }
        assert settings.addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls).is(expectedResolver)
    }

    @Test public void testAddFlatFileResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        File[] expectedDirs = ['a' as File]
        context.checking {
            one(dependencyManagerMock).addFlatDirResolver(expectedName, expectedDirs);
            will(returnValue(expectedResolver))
        }
        assert settings.addFlatDirResolver(expectedName, expectedDirs).is(expectedResolver)
    }

    @Test public void testResolver() {
        ResolverContainer expectedResolverContainer = new ResolverContainer()
        context.checking {
            allowing(dependencyManagerMock).getClasspathResolvers();
            will(returnValue(expectedResolverContainer))
        }
        assert settings.resolvers.is(expectedResolverContainer)
    }

    @Test public void testCreateClassLoaderWithNonExistingBuildSource() {
        checkCreateClassLoader([])
    }

    @Test public void testCreateClassLoaderWithExistingBuildSource() {
        List testBuildSourceDependencies = ['dep1' as File]
        checkCreateClassLoader(testBuildSourceDependencies)
    }

    private checkCreateClassLoader(List expectedTestDependencies) {
        Set testFiles = [new File('/root/f1'), new File('/root/f2')] as Set
        File expectedBuildResolverDir = 'expectedBuildResolverDir' as File
        StartParameter expectedStartParameter = settings.buildSrcStartParameter.newInstance();
        expectedStartParameter.setCurrentDir(new File(settingsDir, DefaultSettings.DEFAULT_BUILD_SRC_DIR))
        ConfigurationResolver configuration = context.mock(ConfigurationResolver.class)
        context.checking {
            allowing(dependencyManagerMock).getBuildResolverDir(); will(returnValue(expectedBuildResolverDir))
            allowing(dependencyManagerMock).configuration(DefaultSettings.BUILD_CONFIGURATION)
            will(returnValue(configuration))
            allowing(configuration).getFiles()
            will(returnValue(testFiles))
        }
        URLClassLoader createdClassLoader = null

        context.checking {
            one(buildSourceBuilderMock).createBuildSourceClasspath(expectedStartParameter)
            will(returnValue(expectedTestDependencies))
        }
        createdClassLoader = settings.createClassLoader()


        Set urls = createdClassLoader.URLs as HashSet
        (testFiles + expectedTestDependencies).collect() {File file -> file.toURI().toURL()}.each {
            assert urls.contains(it)
        }
    }

    @Test public void testCanGetAndSetDynamicProperties() {
        settings.dynamicProp = 'value'
        assertEquals('value', settings.dynamicProp)
    }

    @Test (expected = MissingPropertyException) public void testPropertyMissing() {
        settings.unknownProp
    }

    @Test public void testGetRootDir() {
        assertEquals(settingsDir, settings.rootDir);
    }

    @Test public void testHasUsefulToString() {
        assertEquals('settings \'root\'', settings.toString())
    }

}
