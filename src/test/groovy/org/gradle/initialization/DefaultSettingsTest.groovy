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
import org.gradle.api.plugins.JavaPlugin

/**
 * @author Hans Dockter
 */
class DefaultSettingsTest extends GroovyTestCase {
    File currentDir
    File rootDir
    File gradleUserHomeDir
    DefaultDependencyManager dependencyManager
    BuildSourceBuilder buildSourceBuilder
    DefaultSettings settings
    StubFor dependencyManagerMocker
    MockFor buildSourceBuilderMocker

    void setUp() {
        rootDir = new File('/root')
        currentDir = new File(rootDir, 'current')
        gradleUserHomeDir = new File('gradleUserHomeDir')
        dependencyManager = new DefaultDependencyManager()
        DependencyManagerFactory dependencyManagerFactory = [createDependencyManager: {->
            assertEquals(this.rootDir, rootDir)
            dependencyManager
        }] as DependencyManagerFactory
        buildSourceBuilder = new BuildSourceBuilder(new EmbeddedBuildExecuter())
        settings = new DefaultSettings(currentDir, rootDir, dependencyManagerFactory, buildSourceBuilder, gradleUserHomeDir)
        dependencyManagerMocker = new StubFor(DefaultDependencyManager)
        buildSourceBuilderMocker = new MockFor(BuildSourceBuilder)
    }

    void tearDown() {
        dependencyManagerMocker.expect.verify()
    }

    void testSettings() {
        assert settings.currentDir.is(currentDir)
        assert settings.rootDir.is(rootDir)
        assert settings.dependencyManager.is(dependencyManager)
        assert settings.dependencyManager.configurations[DefaultSettings.BUILD_CONFIGURATION]
        assertEquals(gradleUserHomeDir.absolutePath, settings.dependencyManager.project.gradleUserHome)
        assertNotNull(settings.dependencyManager.project.name)
        assertNotNull(settings.dependencyManager.project.group)
        assertNotNull(settings.dependencyManager.project.version)
        assert settings.buildSourceBuilder.is(buildSourceBuilder)
        assertEquals(DefaultSettings.DEFAULT_BUILD_SRC_DIR, settings.buildSrcDir)
        assertEquals(Project.DEFAULT_PROJECT_FILE, settings.buildSrcScriptName)
        assertEquals([JavaPlugin.CLEAN, JavaPlugin.INSTALL], settings.buildSrcTaskNames)
        assertEquals([:], settings.buildSrcProjectProperties)
        assertEquals([:], settings.buildSrcSystemProperties)
        assertTrue(settings.buildSrcSearchUpwards)
        assertTrue(settings.buildSrcRecursive)
    }

    void testInclude() {
        String[] paths1 = ['a', 'b']
        String[] paths2 = ['c', 'd']
        settings.include(paths1)
        assertEquals(paths1 as List, settings.projectPaths)
        settings.include(paths2)
        assertEquals((paths1 as List) + (paths2 as List), settings.projectPaths)
    }

    void testAddDependencies() {
        String[] expectedDependencies = ["dep1", "dep2"]
        dependencyManagerMocker.demand.addDependencies(1..1) {List confs, Object[] dependencies ->
            assertEquals([DefaultSettings.BUILD_CONFIGURATION], confs)
            assertArrayEquals expectedDependencies, dependencies
        }
        dependencyManagerMocker.use(dependencyManager) {
            settings.addDependencies(expectedDependencies)
        }
    }

    void testResolver() {
        List expectedResolvers = ["r1", "r2"]
        dependencyManagerMocker.demand.setResolvers(1..1) {List resolvers ->
            assert resolvers.is(expectedResolvers)
        }
        dependencyManagerMocker.demand.getResolvers(1..1) {expectedResolvers}
        dependencyManagerMocker.use(dependencyManager) {
            settings.resolvers = expectedResolvers
            assert settings.resolvers.is(expectedResolvers)
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
        dependencyManagerMocker.demand.addDependencies(1..1) {List confs, Object[] dependencies ->
            assertEquals([DefaultSettings.BUILD_CONFIGURATION], confs)
            assertEquals([testDependency], dependencies as List)
        }
        checkCreateClassLoader(testDependency)
    }

    private checkCreateClassLoader(def expectedDependency, boolean srcBuilderNull = false) {
        List testFiles = [new File('/root/f1'), new File('/root/f2')]
        File expectedBuildResolverDir = 'expectedBuildResolverDir' as File
        buildSourceBuilderMocker.demand.createDependency(1..1) {File buildSrcDir, File buildResolverDir,
                                                                String buildScriptName,
                                                                List taskNames, Map projectProperties, Map systemProperties,
                                                                boolean recursive, boolean searchUpwards ->
            assertEquals(new File(settings.rootDir, settings.buildSrcDir), buildSrcDir)
            assertEquals(expectedBuildResolverDir, buildResolverDir)
            assert buildScriptName.is(settings.buildSrcScriptName)
            assert taskNames.is(settings.buildSrcTaskNames)
            assert projectProperties.is(settings.buildSrcProjectProperties)
            assert systemProperties.is(settings.buildSrcSystemProperties)
            assertEquals(settings.buildSrcRecursive, recursive)
            assertEquals(settings.buildSrcSearchUpwards, searchUpwards)
            expectedDependency
        }
        dependencyManagerMocker.demand.getBuildResolverDir(0..1) {->
            expectedBuildResolverDir
        }
        dependencyManagerMocker.demand.resolveClasspath(1..1) {String configuration ->
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
}
