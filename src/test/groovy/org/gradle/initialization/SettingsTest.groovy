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
import org.gradle.api.internal.dependencies.DefaultDependencyManager

/**
 * @author Hans Dockter
 */
class SettingsTest extends GroovyTestCase {
    File currentDir
    File rootDir
    DefaultDependencyManager dependencyManager
    DefaultSettings settings

    void setUp() {
        rootDir = new File('/root')
        currentDir = new File(rootDir, 'current')
        dependencyManager = new DefaultDependencyManager()
        settings = new DefaultSettings(currentDir, rootDir, dependencyManager)
    }

    void testSettings() {
        assert settings.currentDir.is(currentDir)
        assert settings.rootDir.is(rootDir)
        assert settings.dependencyManager.is(dependencyManager)
        assert settings.dependencyManager.configurations[DefaultSettings.BUILD_CONFIGURATION]
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
        MockFor dependencyManagerMocker = new MockFor(DefaultDependencyManager)
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
        MockFor dependencyManagerMocker = new MockFor(DefaultDependencyManager)
        dependencyManagerMocker.demand.setResolvers(1..1) {List resolvers ->
            assert resolvers.is(expectedResolvers)
        }
        dependencyManagerMocker.demand.getResolvers(1..1) { expectedResolvers }
        dependencyManagerMocker.use(dependencyManager) {
            settings.resolvers = expectedResolvers
            assert settings.resolvers.is(expectedResolvers)
        }
    }

    void testCreateClassLoader() {
        List testFiles = [new File('/root/f1'), new File('/root/f2')]
        MockFor dependencyManagerMocker = new MockFor(DefaultDependencyManager)
        dependencyManagerMocker.demand.resolveClasspath(1..1) {String configuration ->
            assertEquals(DefaultSettings.BUILD_CONFIGURATION, configuration)
            testFiles
        }
        URLClassLoader createdClassLoader = null
        dependencyManagerMocker.use(dependencyManager) {
            createdClassLoader = settings.createClassLoader()
        }
        Set urls = createdClassLoader.URLs as HashSet
        assertEquals((testFiles.collect() { File file -> file.toURL() }) as HashSet, urls)
    }
}
