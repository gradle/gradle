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

package org.gradle.api.internal.project

import org.gradle.api.internal.project.BuildScriptFinder
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
*/
class BuildScriptFinderTest extends GroovyTestCase {
    BuildScriptFinder buildScriptFinder

    DefaultProject rootProject

    String expectedBuildFileName

    void setUp() {
        expectedBuildFileName = 'testfile'
        buildScriptFinder = new BuildScriptFinder(expectedBuildFileName)
        File testDir = HelperUtil.makeNewTestDir()
        File rootDir = new File(testDir, 'root')
        rootDir.mkdir()
        rootProject = HelperUtil.createRootProject(rootDir)
    }

    void testWithExistingProjectFileForRootProject() {
        String expectedScriptText = 'sometext'
        File buildFile = new File(rootProject.rootDir, expectedBuildFileName)
        buildFile.deleteOnExit()
        buildFile.withPrintWriter { PrintWriter writer -> writer.write(expectedScriptText) }
        assertEquals(expectedScriptText, buildScriptFinder.getBuildScript(rootProject))
    }

    void testWithExistingProjectFileForChildProject() {
        String childProjectName = 'child'
        File childProjectDir = new File(rootProject.rootDir, childProjectName)
        childProjectDir.mkdir()
        DefaultProject childProject = rootProject.addChildProject(childProjectName)
        String expectedScriptText = 'sometext'
        File buildFile = new File(childProjectDir, expectedBuildFileName)
        buildFile.deleteOnExit()
        buildFile.withPrintWriter { PrintWriter writer -> writer.write(expectedScriptText) }
        assertEquals(expectedScriptText, buildScriptFinder.getBuildScript(childProject))
    }

    void testWithNonExistingProjectFile() {
        assertEquals('', buildScriptFinder.getBuildScript(rootProject))
    }
}