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

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.repositories.InternalRepository
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.artifacts.ConfigurationContainer
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory
import org.gradle.api.internal.artifacts.DefaultResolverContainer
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.ResolverProvider
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolverFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.BuildSourceBuilder
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.initialization.DefaultSettings
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class DefaultSettingsTest {
    File settingsDir
    StartParameter startParameter
    Map gradleProperties
    BuildSourceBuilder buildSourceBuilderMock
    ScriptSource scriptSourceMock
    DefaultSettings settings
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    DefaultProjectDescriptorRegistry projectDescriptorRegistry;

    ConfigurationContainerFactory configurationContainerFactoryStub = context.mock(ConfigurationContainerFactory.class);
    ConfigurationContainer configurationContainerStub = context.mock(ConfigurationContainer.class);
    Configuration configurationStub = context.mock(Configuration.class);
    InternalRepository internalRepositoryDummy = context.mock(InternalRepository.class);
    DependencyFactory dependencyFactoryStub = context.mock(DependencyFactory)
    ResolverContainer resolverContainerMock = context.mock(ResolverContainer.class);

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        settingsDir = new File('/somepath/root').absoluteFile
        gradleProperties = [someGradleProp: 'someValue']
        startParameter = new StartParameter(currentDir: new File(settingsDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'))
        buildSourceBuilderMock = context.mock(BuildSourceBuilder)
        scriptSourceMock = context.mock(ScriptSource)

        projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
        context.checking {
            one(configurationContainerFactoryStub).createConfigurationContainer(withParam(any(ResolverProvider)), withParam(any(DependencyMetaDataProvider)))
            will(returnValue(configurationContainerStub))
            one(configurationContainerStub).add("build")
            will(returnValue(configurationStub))
        }
        settings = new DefaultSettings(dependencyFactoryStub, resolverContainerMock,
                configurationContainerFactoryStub, internalRepositoryDummy,
                projectDescriptorRegistry, buildSourceBuilderMock, settingsDir, scriptSourceMock, startParameter)
    }

    @Test public void testSettings() {
        assert settings.startParameter.is(startParameter)
        assertEquals(settingsDir, settings.getSettingsDir())
        assert settings.buildConfiguration.is(configurationStub)

        assert settings.buildSourceBuilder.is(buildSourceBuilderMock)
        assertNull(settings.buildSrcStartParameter.buildFile)
        assertEquals([JavaPlugin.CLEAN, Configurations.uploadInternalTaskName(Dependency.MASTER_CONFIGURATION)],
                settings.buildSrcStartParameter.taskNames)
        assertTrue(settings.buildSrcStartParameter.searchUpwards)
        assertNull(settings.getRootProject().getParent())
        assertEquals(settingsDir, settings.getRootProject().getProjectDir())
        assertEquals(settings.getRootProject().getProjectDir().getName(), settings.getRootProject().getName())
        assertEquals(settings.rootProject.buildFileName, Project.DEFAULT_BUILD_FILE);
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
        assertEquals(testDir.canonicalFile, projectDescriptor.getProjectDir())
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
        String[] dependencyNotations = ["dep1", "dep2"]
        Dependency dependencyDummy1 = [:] as Dependency
        Dependency dependencyDummy2 = [:] as Dependency
        context.checking {
            allowing(dependencyFactoryStub).createDependency(dependencyNotations[0]); will(returnValue(dependencyDummy1))
            allowing(dependencyFactoryStub).createDependency(dependencyNotations[1]); will(returnValue(dependencyDummy2))
            one(configurationStub).addDependency(dependencyDummy1)
            one(configurationStub).addDependency(dependencyDummy2)
        }
        settings.dependencies(dependencyNotations)
    }

    @Test public void testDependencyWithClosure() {
        String dependencyNotation = "dep1"
        Dependency dependencyDummy = [:] as Dependency
        Closure configureClosure = {}
        context.checking {
            allowing(dependencyFactoryStub).createDependency(dependencyNotation, configureClosure); will(returnValue(dependencyDummy))
            one(configurationStub).addDependency(dependencyDummy)
        }

        settings.dependency(dependencyNotation, configureClosure)
    }

    @Test public void testClientModule() {
        String id = "dep1"
        ClientModule clientModuleDummy = [:] as ClientModule
        Closure configureClosure = {}
        context.checking {
            allowing(dependencyFactoryStub).createModule(id, configureClosure); will(returnValue(clientModuleDummy))
            one(configurationStub).addDependency(clientModuleDummy)
        }
        settings.clientModule(id, configureClosure)
    }

    @Test public void testAddMavenRepo() {
        settings.setResolverContainer(resolverContainerMock)
        DualResolver expectedResolver = new DualResolver()
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        context.checking {
            one(resolverContainerMock).addMavenRepo(expectedJarRepoUrls); will(returnValue(expectedResolver))
        }
        assert settings.addMavenRepo(expectedJarRepoUrls).is(expectedResolver)
    }

    @Test public void testAddMavenStyleRepo() {
        DualResolver expectedResolver = new DualResolver()
        String expectedName = 'somename'
        String expectedRoot = 'http://www.root.org'
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        context.checking {
            one(resolverContainerMock).addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls);
            will(returnValue(expectedResolver))
        }
        assert settings.addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls).is(expectedResolver)
    }

    @Test public void testAddFlatFileResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        File[] expectedDirs = ['a' as File]
        context.checking {
            one(resolverContainerMock).addFlatDirResolver(expectedName, expectedDirs);
            will(returnValue(expectedResolver))
        }
        assert settings.addFlatDirResolver(expectedName, expectedDirs).is(expectedResolver)
    }

    @Test public void testResolver() {
        settings.setResolverContainer(new DefaultResolverContainer(new DefaultResolverFactory(), null))
        DependencyResolver resolver = settings.addMavenRepo();
        assertEquals([resolver], settings.resolvers)
    }

    @Test public void testCreateClassLoaderWithNonExistingBuildSource() {
        checkCreateClassLoader([] as Set)
    }

    @Test public void testCreateClassLoaderWithExistingBuildSource() {
        Set testBuildSourceDependencies = ['dep1' as File]
        checkCreateClassLoader(testBuildSourceDependencies)
    }

    private checkCreateClassLoader(Set expectedTestDependencies) {
        Set testFiles = [new File('/root/f1'), new File('/root/f2')] as Set
        File expectedBuildResolverDir = 'expectedBuildResolverDir' as File
        StartParameter expectedStartParameter = settings.buildSrcStartParameter.newInstance();
        expectedStartParameter.setCurrentDir(new File(settingsDir, DefaultSettings.DEFAULT_BUILD_SRC_DIR))
        context.checking {
            allowing(configurationStub).getFiles()
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
