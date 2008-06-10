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
import groovy.mock.interceptor.StubFor
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.Project
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.plugins.JavaPlugin
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.dependencies.ResolverContainer
import org.apache.ivy.plugins.resolver.DualResolver
import org.gradle.StartParameter

/**
 * @author Hans Dockter
 */
class DefaultSettingsTest extends GroovyTestCase {
    RootFinder rootFinder
    StartParameter startParameter

    DefaultDependencyManager dependencyManager
    BuildSourceBuilder buildSourceBuilder
    DefaultSettings settings
    StubFor dependencyManagerMocker
    MockFor buildSourceBuilderMocker

    void setUp() {
        rootFinder = new RootFinder()
        rootFinder.rootDir = new File('/root')
        rootFinder.gradleProperties.someGradleProp = 'someValue'
        startParameter = new StartParameter(currentDir: new File(rootFinder.rootDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'))
        dependencyManager = new DefaultDependencyManager()
        DependencyManagerFactory dependencyManagerFactory = [createDependencyManager: {->
            dependencyManager
        }] as DependencyManagerFactory
        buildSourceBuilder = new BuildSourceBuilder(new EmbeddedBuildExecuter())
        settings = new DefaultSettings(dependencyManagerFactory, buildSourceBuilder, rootFinder, startParameter)
        dependencyManagerMocker = new StubFor(DefaultDependencyManager)
        buildSourceBuilderMocker = new MockFor(BuildSourceBuilder)
    }

    void tearDown() {
        dependencyManagerMocker.expect.verify()
    }

    void testSettings() {
        assert settings.startParameter.is(startParameter)
        assert settings.rootFinder.is(rootFinder)
        assert settings.dependencyManager.is(dependencyManager)
        assert settings.dependencyManager.configurations[DefaultSettings.BUILD_CONFIGURATION]
        assertEquals(startParameter.gradleUserHomeDir.absolutePath, settings.dependencyManager.project.gradleUserHome)
        assertNotNull(settings.dependencyManager.project.name)
        assertNotNull(settings.dependencyManager.project.group)
        assertNotNull(settings.dependencyManager.project.version)
        assert settings.buildSourceBuilder.is(buildSourceBuilder)
        assertEquals(Project.DEFAULT_PROJECT_FILE, settings.buildSrcStartParameter.buildFileName)
        assertEquals([JavaPlugin.CLEAN, JavaPlugin.UPLOAD_LIBS], settings.buildSrcStartParameter.taskNames)
        assertTrue(settings.buildSrcStartParameter.searchUpwards)
        assertTrue(settings.buildSrcStartParameter.recursive)
    }

    void testInclude() {
        String[] paths1 = ['a', 'b']
        String[] paths2 = ['c', 'd']
        settings.include(paths1)
        assertEquals(paths1 as List, settings.projectPaths)
        settings.include(paths2)
        assertEquals((paths1 as List) + (paths2 as List), settings.projectPaths)
    }

    void testDependencies() {
        String[] expectedDependencies = ["dep1", "dep2"]
        dependencyManagerMocker.demand.dependencies(1..1) {List confs, Object[] dependencies ->
            assertEquals([DefaultSettings.BUILD_CONFIGURATION], confs)
            assertArrayEquals expectedDependencies, dependencies
        }
        dependencyManagerMocker.use(dependencyManager) {
            settings.dependencies(expectedDependencies)
        }
    }

    void testDependency() {
        String expectedId = "dep1"
        Closure expectedConfigureClosure
        dependencyManagerMocker.demand.dependency(1..1) {List confs, String id, Closure configureClosure ->
            assertEquals([DefaultSettings.BUILD_CONFIGURATION], confs)
            assertEquals(expectedId, id)
            assertEquals(expectedConfigureClosure, configureClosure)
        }
        dependencyManagerMocker.use(dependencyManager) {
            settings.dependency(expectedId, expectedConfigureClosure)
        }
    }

    void testClientModule() {
        String expectedId = "dep1"
        Closure expectedConfigureClosure
        dependencyManagerMocker.demand.clientModule(1..1) {List confs, String id, Closure configureClosure ->
            assertEquals([DefaultSettings.BUILD_CONFIGURATION], confs)
            assertEquals(expectedId, id)
            assertEquals(expectedConfigureClosure, configureClosure)
        }
        dependencyManagerMocker.use(dependencyManager) {
            settings.clientModule(expectedId, expectedConfigureClosure)
        }
    }

    void testAddMavenRepo() {
        DualResolver expectedResolver = new DualResolver()
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        dependencyManagerMocker.demand.addMavenRepo(1..1) {String[] jarRepoUrls ->
            assertArrayEquals(expectedJarRepoUrls, jarRepoUrls)
            expectedResolver
        }
        dependencyManagerMocker.use(dependencyManager) {
            assert settings.addMavenRepo(expectedJarRepoUrls).is(expectedResolver)
        }
    }

    void testAddMavenStyleRepo() {
        DualResolver expectedResolver = new DualResolver()
        String expectedName = 'somename'
        String expectedRoot = 'http://www.root.org'
        String[] expectedJarRepoUrls = ['http://www.repo.org']
        dependencyManagerMocker.demand.addMavenStyleRepo(1..1) {String name, String root, String[] jarRepoUrls ->
            assertEquals(expectedName, name)
            assertEquals(expectedRoot, root)
            assertArrayEquals(expectedJarRepoUrls, jarRepoUrls)
            expectedResolver
        }
        dependencyManagerMocker.use(dependencyManager) {
            assert settings.addMavenStyleRepo(expectedName, expectedRoot, expectedJarRepoUrls).is(expectedResolver)
        }
    }

    void testAddFlatFileResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        File[] expectedDirs = ['a' as File]
        dependencyManagerMocker.demand.addFlatDirResolver(1..1) {String name, File[] dirs ->
            assertEquals(expectedName, name)
            assertArrayEquals(expectedDirs, dirs)
            expectedResolver
        }
        dependencyManagerMocker.use(dependencyManager) {
            assert settings.addFlatDirResolver(expectedName, expectedDirs).is(expectedResolver)
        }
    }

    void testCreateFlatFileResolver() {
        FileSystemResolver expectedResolver = new FileSystemResolver()
        String expectedName = 'name'
        File[] expectedDirs = ['a' as File]
        dependencyManagerMocker.demand.createFlatDirResolver(1..1) {String name, File[] dirs ->
            assertEquals(expectedName, name)
            assertArrayEquals(expectedDirs, dirs)
            expectedResolver
        }
        dependencyManagerMocker.use(dependencyManager) {
            assert settings.createFlatDirResolver(expectedName, expectedDirs).is(expectedResolver)
        }
    }

    void testResolver() {
        ResolverContainer expectedResolverContainer = new ResolverContainer()
        dependencyManagerMocker.demand.getClasspathResolvers(1..1) {expectedResolverContainer}
        dependencyManagerMocker.use(dependencyManager) {
            assert settings.resolvers.is(expectedResolverContainer)
        }
    }

    void testCreateClassLoaderWithNullBuildSourceBuilder() {
        checkCreateClassLoader(null, true)
    }

    void testCreateClassLoaderWithNonExistingBuildSource() {
        checkCreateClassLoader(null)
    }

    void testCreateClassLoaderWithExistingBuildSource() {
        String testDependency = 'org.gradle:somedep:1.0'
        dependencyManagerMocker.demand.dependencies(1..1) {List confs, Object[] dependencies ->
            assertEquals([DefaultSettings.BUILD_CONFIGURATION], confs)
            assertEquals([testDependency], dependencies as List)
        }
        checkCreateClassLoader(testDependency)
    }

    private checkCreateClassLoader(def expectedDependency, boolean srcBuilderNull = false) {
        List testFiles = [new File('/root/f1'), new File('/root/f2')]
        File expectedBuildResolverDir = 'expectedBuildResolverDir' as File
        buildSourceBuilderMocker.demand.createDependency(1..1) {File buildResolverDir, StartParameter startParameter ->
            assertEquals(expectedBuildResolverDir, buildResolverDir)
            assertEquals(StartParameter.newInstance(settings.buildSrcStartParameter,
                    currentDir: new File(settings.rootFinder.rootDir, DefaultSettings.DEFAULT_BUILD_SRC_DIR)), startParameter)
            expectedDependency
        }
        dependencyManagerMocker.demand.getBuildResolverDir(0..1) {->
            expectedBuildResolverDir
        }
        dependencyManagerMocker.demand.resolve(1..1) {String configuration ->
            assertEquals(DefaultSettings.BUILD_CONFIGURATION, configuration)
            testFiles
        }
        URLClassLoader createdClassLoader = null

        if (srcBuilderNull) {
            settings.buildSourceBuilder = null
            dependencyManagerMocker.use(dependencyManager) {
                createdClassLoader = settings.createClassLoader()
            }
        } else {
            buildSourceBuilderMocker.use(buildSourceBuilder) {
                dependencyManagerMocker.use(dependencyManager) {
                    createdClassLoader = settings.createClassLoader()
                }
            }
        }

        Set urls = createdClassLoader.URLs as HashSet
        assertEquals((testFiles.collect() {File file -> file.toURL()}) as HashSet, urls)
    }

    void testPropertyMissing() {
        assert settings.rootDir.is(rootFinder.rootDir)
        assert settings.currentDir.is(startParameter.currentDir)
        assert settings.someGradleProp.is(rootFinder.gradleProperties.someGradleProp)
        shouldFail(MissingPropertyException) {
            settings.unknownProp
        }
    }
}
