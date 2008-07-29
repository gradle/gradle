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

import groovy.mock.interceptor.MockFor
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.StartParameter
import org.gradle.api.DependencyManager
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.Project
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class DefaultSettingsTest {
    RootFinder rootFinder
    StartParameter startParameter

    DependencyManager dependencyManager
    BuildSourceBuilder buildSourceBuilder
    DefaultSettings settings
    MockFor buildSourceBuilderMocker

    Project project
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        rootFinder = new RootFinder()
        rootFinder.rootDir = new File('/root')
        rootFinder.gradleProperties.someGradleProp = 'someValue'
        startParameter = new StartParameter(currentDir: new File(rootFinder.rootDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'))
        dependencyManager = context.mock(DependencyManager)
        DependencyManagerFactory dependencyManagerFactory = [createDependencyManager: {Project project ->
            this.project = project
            context.checking {
                allowing(dependencyManager).getProject(); will(returnValue(project))
                allowing(dependencyManager).addConfiguration(DefaultSettings.BUILD_CONFIGURATION);
            }
            dependencyManager
        }] as DependencyManagerFactory
        buildSourceBuilder = new BuildSourceBuilder(new EmbeddedBuildExecuter())
        settings = new DefaultSettings(dependencyManagerFactory, buildSourceBuilder, rootFinder, startParameter)
        buildSourceBuilderMocker = new MockFor(BuildSourceBuilder)
    }

    @Test public void testSettings() {
        assert settings.startParameter.is(startParameter)
        assert settings.rootFinder.is(rootFinder)
        assert settings.dependencyManager.is(dependencyManager)
        assertEquals(startParameter.gradleUserHomeDir.absolutePath, settings.dependencyManager.project.gradleUserHome)
        assertNotNull(project.name)
        assertNotNull(project.group)
        assertNotNull(project.version)
        assert settings.buildSourceBuilder.is(buildSourceBuilder)
        assertEquals(Project.DEFAULT_PROJECT_FILE, settings.buildSrcStartParameter.buildFileName)
        assertEquals([JavaPlugin.CLEAN, JavaPlugin.UPLOAD_LIBS], settings.buildSrcStartParameter.taskNames)
        assertTrue(settings.buildSrcStartParameter.searchUpwards)
        assertTrue(settings.buildSrcStartParameter.recursive)
    }

    @Test public void testInclude() {
        String[] paths1 = ['a', 'b']
        String[] paths2 = ['c', 'd']
        settings.include(paths1)
        assertEquals(paths1 as List, settings.projectPaths)
        settings.include(paths2)
        assertEquals((paths1 as List) + (paths2 as List), settings.projectPaths)
    }

    @Test public void testDependencies() {
        String[] expectedDependencies = ["dep1", "dep2"]
        context.checking {
            one(dependencyManager).dependencies([DefaultSettings.BUILD_CONFIGURATION], expectedDependencies)
        }
        settings.dependencies(expectedDependencies)
    }

    @Test public void testDependency() {
        String expectedId = "dep1"
        Closure expectedConfigureClosure
        context.checking {
            one(dependencyManager).dependency([DefaultSettings.BUILD_CONFIGURATION], expectedId, expectedConfigureClosure)
        }
        settings.dependency(expectedId, expectedConfigureClosure)
    }

    @Test public void testClientModule() {
        String expectedId = "dep1"
        Closure expectedConfigureClosure
        context.checking {
            one(dependencyManager).clientModule([DefaultSettings.BUILD_CONFIGURATION], expectedId, expectedConfigureClosure)
        }
        settings.clientModule(expectedId, expectedConfigureClosure)
    }

    @Test public void testAddMavenRepo() {
        DualResolver expectedResolver = new DualResolver()
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        context.checking {
            one(dependencyManager).addMavenRepo(expectedJarRepoUrls); will(returnValue(expectedResolver))
        }
        assert settings.addMavenRepo(expectedJarRepoUrls).is(expectedResolver)
    }

    @Test public void testAddMavenStyleRepo() {
        DualResolver expectedResolver = new DualResolver()
        String expectedName = 'somename'
        String expectedRoot = 'http://www.root.org'
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        context.checking {
            one(dependencyManager).addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls); will(returnValue(expectedResolver))
        }
        assert settings.addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls).is(expectedResolver)
    }

    @Test public void testAddFlatFileResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        File[] expectedDirs = ['a' as File]
        context.checking {
            one(dependencyManager).addFlatDirResolver(expectedName, expectedDirs); will(returnValue(expectedResolver))
        }
        assert settings.addFlatDirResolver(expectedName, expectedDirs).is(expectedResolver)
    }

    @Test public void testResolver() {
        ResolverContainer expectedResolverContainer = new ResolverContainer()
        context.checking {
            one(dependencyManager).getClasspathResolvers(); will(returnValue(expectedResolverContainer))
        }
        assert settings.resolvers.is(expectedResolverContainer)
    }

    @Test public void testCreateClassLoaderWithNullBuildSourceBuilder() {
        checkCreateClassLoader(null, true)
    }

    @Test public void testCreateClassLoaderWithNonExistingBuildSource() {
        checkCreateClassLoader(null)
    }

    @Test public void testCreateClassLoaderWithExistingBuildSource() {
        String testDependency = 'org.gradle:somedep:1.0'
        context.checking {
            one(dependencyManager).dependencies([DefaultSettings.BUILD_CONFIGURATION], WrapUtil.toArray(testDependency));
        }
        checkCreateClassLoader(testDependency)
    }

    private checkCreateClassLoader(def expectedDependency, boolean srcBuilderNull = false) {
        List testFiles = [new File('/root/f1'), new File('/root/f2')]
        File expectedBuildResolverDir = 'expectedBuildResolverDir' as File
        buildSourceBuilderMocker.demand.createDependency(1..1) {File buildResolverDir, StartParameter startParameter ->
            assertEquals(expectedBuildResolverDir, buildResolverDir)
            StartParameter expectedStartParameter = StartParameter.newInstance(settings.buildSrcStartParameter);
            expectedStartParameter.setCurrentDir(new File(settings.rootFinder.rootDir, DefaultSettings.DEFAULT_BUILD_SRC_DIR))
            assertEquals(expectedStartParameter, startParameter)
            expectedDependency
        }
        context.checking {
            allowing(dependencyManager).getBuildResolverDir(); will(returnValue(expectedBuildResolverDir))
            one(dependencyManager).resolve(DefaultSettings.BUILD_CONFIGURATION); will(returnValue(testFiles))
        }
        URLClassLoader createdClassLoader = null

        if (srcBuilderNull) {
            settings.buildSourceBuilder = null
            createdClassLoader = settings.createClassLoader()
        } else {
            buildSourceBuilderMocker.use(buildSourceBuilder) {
                createdClassLoader = settings.createClassLoader()
            }
        }

        Set urls = createdClassLoader.URLs as HashSet
        testFiles.collect() {File file -> file.toURI().toURL()}.each {
            assert urls.contains(it)
        }
    }

    @Test (expected = MissingPropertyException) public void testPropertyMissing() {
        assert settings.rootDir.is(rootFinder.rootDir)
        assert settings.currentDir.is(startParameter.currentDir)
        assert settings.someGradleProp.is(rootFinder.gradleProperties.someGradleProp)
        settings.unknownProp
    }
}
