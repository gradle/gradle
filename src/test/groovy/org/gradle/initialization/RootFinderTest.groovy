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

/**
* @author Hans Dockter
*/
class RootFinderTest extends GroovyTestCase {
    static final String TEST_SETTINGS_TEXT = 'somescriptcode'
    RootFinder rootFinder
    File rootDir
    File currentDir

    void setUp() {
        rootFinder = new RootFinder()
        rootDir = HelperUtil.makeNewTestDir()
        File inbetweenDir = new File(rootDir, "inbetween")
        inbetweenDir.mkdir()
        inbetweenDir.deleteOnExit()
        currentDir = new File(inbetweenDir, "current")
        currentDir.mkdir()
        currentDir.deleteOnExit()
    }

    void testGradleSettingsInCurrentDirWithSearchUpwardsTrue() {
        createSettingsFile(currentDir)
        rootFinder.find(currentDir, true)
        assertEquals(TEST_SETTINGS_TEXT, rootFinder.settingsText)
        assertEquals(currentDir, rootFinder.rootDir)
    }

    void testGradleSettingsInCurrentDirWithSearchUpwardsFalse() {
        createSettingsFile(currentDir)
        rootFinder.find(currentDir, false)
        assertEquals(TEST_SETTINGS_TEXT, rootFinder.settingsText)
        assertEquals(currentDir, rootFinder.rootDir)
    }

    void testGradleSettingsInUpwardDirWithSearchUpwardsTrue() {
        createSettingsFile(rootDir)
        rootFinder.find(currentDir, true)
        assertEquals(TEST_SETTINGS_TEXT, rootFinder.settingsText)
        assertEquals(rootDir, rootFinder.rootDir)
    }

    void testGradleSettingsInUpwardDirWithSearchUpwardsFalse() {
        createSettingsFile(rootDir)
        rootFinder.find(currentDir, false)
        assert !rootFinder.settingsText
        assertEquals(currentDir, rootFinder.rootDir)
    }

    void testNoGradleSettingsInUpwardDirWithSearchUpwardsTrue() {
        createSettingsFile(rootDir)
        rootFinder.find(currentDir, false)
        assert !rootFinder.settingsText
        assertEquals(currentDir, rootFinder.rootDir)
    }

    private void createSettingsFile(File dir) {
        File file = new File(dir, SettingsProcessor.DEFAULT_SETUP_FILE)
        file.write(TEST_SETTINGS_TEXT)
        file.deleteOnExit()
    }

}