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
import org.gradle.api.DependencyManager
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.SettingsFileHandler
import org.gradle.initialization.SettingsProcessor


/**
 * @author Hans Dockter
 */
class SettingsProcessorTest extends GroovyTestCase {
    static final File TEST_ROOT_DIR = new File('rootDir')
    SettingsProcessor settingsProcessor
    SettingsFileHandler settingsFileHandler
    DependencyManager dependencyManager

    void setUp() {
        settingsFileHandler = new SettingsFileHandler()
        dependencyManager = new DefaultDependencyManager()
        settingsProcessor = new SettingsProcessor(settingsFileHandler, dependencyManager)
    }

    void testSettingsProcessor() {
        assert settingsProcessor.settingsFileHandler.is(settingsFileHandler)
        assert settingsProcessor.dependencyManager.is(dependencyManager)
    }

    void testProcessWithCurrentDirAsSubproject() {
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        List includePaths = ['currentDir', 'path2']
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, currentDir, includePaths)

        assertEquals(includePaths, settings.projectPaths)
        assertSame(currentDir, settings.currentDir)
        assertSame(TEST_ROOT_DIR, settings.rootDir)
    }

    void testProcessWithCurrentDirNoSubproject() {
        File currentDir = new File(TEST_ROOT_DIR, 'currentDir')
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, currentDir, ['path1', 'path2'])

        assertEquals([], settings.projectPaths)
        assertSame(currentDir, settings.rootDir)
        assertSame(currentDir, settings.currentDir)
    }

    void testProcessWithCurrentDirAsRootDir() {
        List includePaths = ['path1', 'path2']
        DefaultSettings settings = runCUT(TEST_ROOT_DIR, TEST_ROOT_DIR, includePaths)

        assertEquals(includePaths, settings.projectPaths)
        assertSame(TEST_ROOT_DIR, settings.currentDir)
        assertSame(TEST_ROOT_DIR, settings.rootDir)
    }

    private DefaultSettings runCUT(File rootDir, File currentDir, List includePaths) {
        MockFor settingsFileHandlerMocker = new MockFor(SettingsFileHandler)
        DefaultDependencyManager expectedDependencyManager = new DefaultDependencyManager()
        String expectedSettingsText = "include \"${includePaths[0]}\", \"${includePaths[1]}\""
        boolean expectedSearchUpwards = false

        settingsFileHandlerMocker.demand.find(1..1) {File dir, boolean searchUpwards ->
            assertSame(currentDir, dir)
            assertEquals(expectedSearchUpwards, searchUpwards)
        }
        settingsFileHandlerMocker.demand.getRootDir() {rootDir}
        settingsFileHandlerMocker.demand.getSettingsText() {expectedSettingsText}

        DefaultSettings settings
        settingsFileHandlerMocker.use {
            SettingsProcessor settingsProcessor = new SettingsProcessor(new SettingsFileHandler(), expectedDependencyManager)
            settings = settingsProcessor.process(currentDir, expectedSearchUpwards)
        }
        assertSame(expectedDependencyManager, settings.dependencyManager)
        settings
    }
}