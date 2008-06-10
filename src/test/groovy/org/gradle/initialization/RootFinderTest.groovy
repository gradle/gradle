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

import org.gradle.initialization.RootFinder
import org.gradle.util.HelperUtil
import org.gradle.api.Project
import org.gradle.StartParameter

/**
 * @author Hans Dockter
 */
class RootFinderTest extends GroovyTestCase {
    static final String TEST_SETTINGS_TEXT = 'somescriptcode'
    RootFinder rootFinder
    File testDir
    File rootDir
    File currentDir
    File userHome

    Map expectedGradleProperties

    void setUp() {
        rootFinder = new RootFinder()
        testDir = HelperUtil.makeNewTestDir()
        userHome = new File(testDir, 'userHome')
        userHome.mkdirs()
        rootDir = new File(testDir, 'root')
        rootDir.mkdirs()
        File inbetweenDir = new File(rootDir, "inbetween")
        inbetweenDir.mkdir()
        inbetweenDir.deleteOnExit()
        currentDir = new File(inbetweenDir, "current")
        currentDir.mkdir()
        currentDir.deleteOnExit()
        expectedGradleProperties = [prop1: 'value1UserHome', prop2: 'value2', prop3: 'value3']
    }

    protected void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testInit() {
        assertEquals([:], rootFinder.gradleProperties)
    }

    void testGradleSettingsInCurrentDirWithSearchUpwardsTrue() {
        createSettingsFile(currentDir)
        rootFinder.find(createStartParams(currentDir, true))
        checkRootFinder(TEST_SETTINGS_TEXT, currentDir)
    }

    void testGradleSettingsInCurrentDirWithSearchUpwardsFalse() {
        createSettingsFile(currentDir)
        rootFinder.find(createStartParams(currentDir, false))
        checkRootFinder(TEST_SETTINGS_TEXT, currentDir)
    }

    void testGradleSettingsInUpwardDirWithSearchUpwardsTrue() {
        createSettingsFile(rootDir)
        rootFinder.find(createStartParams(currentDir, true))
        checkRootFinder(TEST_SETTINGS_TEXT, rootDir)
    }

    void testGradleSettingsInUpwardDirWithSearchUpwardsFalse() {
        createSettingsFile(rootDir)
        createPropertyFiles(currentDir)
        rootFinder.find(createStartParams(currentDir, false))
        checkRootFinder('', currentDir)
    }

    void testNoGradleSettingsInUpwardDirWithSearchUpwardsTrue() {
        createPropertyFiles(currentDir)
        rootFinder.find(createStartParams(currentDir, true))
        checkRootFinder('', currentDir)
    }

    private void createSettingsFile(File dir) {
        File file = new File(dir, SettingsProcessor.DEFAULT_SETUP_FILE)
        file.write(TEST_SETTINGS_TEXT)
        file.deleteOnExit()
        createPropertyFiles(dir)
    }

    private void createPropertyFiles(File rootDir) {
        Closure createProps = {Map args, File parentFile ->
            Properties properties = new Properties()
            properties.putAll(args)
            properties.store(new FileOutputStream(new File(parentFile, Project.GRADLE_PROPERTIES)), '')
        }
        createProps(userHome, prop1: 'value1UserHome', prop2: 'value2')
        createProps(rootDir, prop1: 'value1RootDir', prop3: 'value3')
    }

    private checkRootFinder(String expectedSettingsText, File expectedRootDir) {
        assertEquals(expectedSettingsText, rootFinder.settingsText)
        assertEquals(expectedGradleProperties, rootFinder.gradleProperties)
        assertEquals(expectedRootDir, rootFinder.rootDir)
    }

    private StartParameter createStartParams(File currentDir, boolean searchUpwards) {
        new StartParameter(currentDir: currentDir, searchUpwards: searchUpwards, gradleUserHomeDir: userHome)    
    }

}