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
import org.gradle.api.DependencyManager
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.RootFinder
import org.gradle.initialization.SettingsProcessor
import org.gradle.util.HelperUtil
import org.gradle.api.internal.project.ImportsReader
import org.gradle.StartParameter

/**
 * @author Hans Dockter
 */
class SettingsProcessorTest extends GroovyTestCase {
    static final File TEST_ROOT_DIR = new File('rootDir')
    SettingsProcessor settingsProcessor
    RootFinder expectedRootFinder
    ImportsReader importsReader
    DependencyManagerFactory dependencyManagerFactory
    SettingsFactory settingsFactory
    BuildSourceBuilder buildSourceBuilder
    StartParameter expectedStartParameter
    File buildResolverDir

    DefaultSettings expectedSettings
    MockFor settingsFactoryMocker

    void setUp() {
        buildResolverDir = HelperUtil.makeNewTestDir()
        expectedSettings = new DefaultSettings()
        expectedStartParameter = new StartParameter()
        expectedRootFinder = new RootFinder()
        importsReader = new ImportsReader()
        settingsFactory = new SettingsFactory()
        dependencyManagerFactory = new DefaultDependencyManagerFactory(new File('root'))
        buildSourceBuilder = new BuildSourceBuilder()
        settingsProcessor = new SettingsProcessor(importsReader, settingsFactory, dependencyManagerFactory, buildSourceBuilder,
                buildResolverDir)

        settingsFactoryMocker = new MockFor(SettingsFactory)
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testSettingsProcessor() {
        assert settingsProcessor.importsReader.is(importsReader)
        assert settingsProcessor.settingsFactory.is(settingsFactory)
        assert settingsProcessor.dependencyManagerFactory.is(dependencyManagerFactory)
        assert settingsProcessor.buildSourceBuilder.is(buildSourceBuilder)
        assert settingsProcessor.buildResolverDir.is(buildResolverDir)
    }

    void testCreateBasicSettings() {
        File expectedCurrentDir = new File(TEST_ROOT_DIR, 'currentDir')
        expectedStartParameter = createStartParameter(expectedCurrentDir)
        prepareSettingsFactoryMocker(expectedCurrentDir, expectedCurrentDir)
        settingsFactoryMocker.use(settingsProcessor.settingsFactory) {
            assert settingsProcessor.createBasicSettings(expectedRootFinder, expectedStartParameter).is(expectedSettings)
        }
        assertEquals([], expectedSettings.projectPaths)
        checkBuildResolverDir(buildResolverDir)
    }

    void testWithNonExistingBuildResolverDir() {
        HelperUtil.deleteTestDir()
        File expectedCurrentDir = new File(TEST_ROOT_DIR, 'currentDir')
        expectedStartParameter = createStartParameter(expectedCurrentDir)
        prepareSettingsFactoryMocker(expectedCurrentDir, expectedCurrentDir)
        settingsFactoryMocker.use(settingsProcessor.settingsFactory) {
            assert settingsProcessor.createBasicSettings(expectedRootFinder, expectedStartParameter).is(expectedSettings)
        }
        assertEquals([], expectedSettings.projectPaths)
        checkBuildResolverDir(buildResolverDir)
    }

    void testProcessWithCurrentDirAsSubproject() {
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        List includePaths = ['currentDir', 'path2']
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, currentDir, includePaths, buildResolverDir)
        assertEquals(includePaths, settings.projectPaths)
    }

    void testProcessWithCurrentDirNoSubproject() {
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, currentDir, ['path1', 'path2'], buildResolverDir) {
            prepareSettingsFactoryMocker(currentDir, currentDir)
        }
        assertEquals([], settings.projectPaths)
    }

    void testProcessWithCurrentDirAsRootDir() {
        List includePaths = ['path1', 'path2']
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, TEST_ROOT_DIR, includePaths, buildResolverDir)
        assertEquals(includePaths, settings.projectPaths)
    }

    void testProcessWithNullBuildResolver() {
        settingsProcessor.buildResolverDir = null
        List includePaths = ['path1', 'path2']
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, TEST_ROOT_DIR, includePaths,
                new File(TEST_ROOT_DIR, DependencyManager.BUILD_RESOLVER_NAME))
        assertEquals(includePaths, settings.projectPaths)
    }

    private void prepareSettingsFactoryMocker(File expectedRootDir, File expectedCurrentDir) {
        expectedSettings.rootFinder = expectedRootFinder
        expectedSettings.startParameter = expectedStartParameter
        settingsFactoryMocker.demand.createSettings(1..1) {DependencyManagerFactory dependencyManagerFactory,
                                                           BuildSourceBuilder buildSourceBuilder, RootFinder rootFinder, StartParameter startParameter ->
            assert dependencyManagerFactory.is(settingsProcessor.dependencyManagerFactory)
            assert buildSourceBuilder.is(settingsProcessor.buildSourceBuilder)
            assert rootFinder.is(expectedRootFinder)
            assertEquals(expectedStartParameter, startParameter)
            expectedSettings.projectPaths = []
            expectedSettings
        }

    }

    private DefaultSettings runCUT(File rootDir, File currentDir, List includePaths, File expectedBuildResolverRoot,
                                   Closure customSettingsFactoryPreparation = {}) {
        ImportsReader mockImportsReader = [getImports: {File importsRootDir ->
            assertEquals(rootDir, importsRootDir)
            '''import org.gradle.api.*
'''
        }] as ImportsReader
        settingsProcessor.importsReader = mockImportsReader
        String expectedSettingsText = """CircularReferenceException exception // check autoimport

include \"${includePaths[0]}\", \"${includePaths[1]}\"
"""
        boolean expectedSearchUpwards = false

        expectedRootFinder.rootDir = rootDir
        expectedRootFinder.settingsText = expectedSettingsText
        expectedStartParameter = createStartParameter(currentDir)

        prepareSettingsFactoryMocker(rootDir, currentDir)
        customSettingsFactoryPreparation()

        DefaultSettings settings
        settingsFactoryMocker.use(settingsProcessor.settingsFactory) {
            settings = settingsProcessor.process(expectedRootFinder, expectedStartParameter)
        }
        checkBuildResolverDir(expectedBuildResolverRoot)
        settings
    }

    private void checkBuildResolverDir(File buildResolverDir) {
        assertEquals(buildResolverDir, settingsProcessor.dependencyManagerFactory.buildResolverDir)
        assert !buildResolverDir.exists()
    }

    StartParameter createStartParameter(File currentDir) {
        StartParameter.newInstance(expectedStartParameter,
                gradleUserHomeDir: new File('gradleUserHomeDir'),
                currentDir: currentDir)
    }
}