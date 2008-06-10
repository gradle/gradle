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

package org.gradle

import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.gradle.api.DependencyManager
import org.gradle.api.Project
import org.gradle.api.internal.project.BuildScriptFinder
import org.gradle.api.internal.project.EmbeddedBuildScriptFinder
import org.gradle.initialization.SettingsProcessor
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 * todo write disabled test 'testMainWithException' as integration test
 */
class MainTest extends GroovyTestCase {
    final static String TEST_DIR_NAME = "/testdir"

    // This property has to be also set as system property gradle.home when running this test
    final static TEST_GRADLE_HOME = 'roadToNowhere'

    StubFor fileStub
    MockFor buildMockFor

    String expectedBuildFileName
    File expectedGradleUserHome
    File expectedGradleImportsFile
    File expectedProjectDir
    List expectedTaskNames = ["clean", "compile"]
    Map expectedSystemProperties
    Map expectedProjectProperties
    boolean expectedRecursive
    boolean expectedSearchUpwards
    String expectedEmbeddedScript

    void setUp() {
        fileStub = new StubFor(File)
        buildMockFor = new MockFor(Build)
        expectedGradleUserHome = new File(Main.DEFAULT_GRADLE_USER_HOME)
        expectedGradleImportsFile = new File(TEST_GRADLE_HOME, Main.IMPORTS_FILE_NAME)
        expectedTaskNames = ["clean", "compile"]
        expectedProjectDir = new File("").canonicalFile
        expectedProjectProperties = [:]
        expectedSystemProperties = [:]
        expectedBuildFileName = Project.DEFAULT_PROJECT_FILE
        expectedRecursive = true
        expectedSearchUpwards = true
        expectedEmbeddedScript = 'somescript'
    }

    void testMainWithSpecifiedNonExistingProjectDirectory() {
        fileStub.demand.getCanonicalFile {new File(TEST_DIR_NAME)}
        fileStub.demand.isDirectory {false}
        buildMockFor.use {
            fileStub.use {
                Main.main(args(["-p", TEST_DIR_NAME]) as String[])
            }
        }
        // The buildMockFor throws an exception, if the main method does not return prematurely (what it should do). 
    }

    void testMainWithoutAnyOptions() {
        checkMain {Main.main(args(["-S"]) + expectedTaskNames as String[])}
    }

    private checkMain(boolean embedded = false, boolean taskList = false, Closure mainCall) {
        Closure checkStartParameter = {StartParameter startParameter, boolean noTasks = false ->
            assertEquals(noTasks ? [] : expectedTaskNames, startParameter.taskNames)
            assertEquals(expectedProjectDir.canonicalFile, startParameter.currentDir.canonicalFile)
            assertEquals(expectedRecursive, startParameter.recursive)
            assertEquals(expectedSearchUpwards, startParameter.searchUpwards)
            assertEquals(expectedProjectProperties, startParameter.projectProperties)
            assertEquals(expectedSystemProperties, startParameter.systemPropertiesArgs)
            assertEquals(expectedGradleUserHome.absoluteFile, startParameter.gradleUserHomeDir.absoluteFile)
        }

        File expectedBuildResolverRoot = new File(expectedProjectDir, DependencyManager.BUILD_RESOLVER_NAME)
        Throwable assertException = null
        SettingsProcessor settingsProcessor = new SettingsProcessor()
        Closure buildFactory = {BuildScriptFinder buildScriptFinder, File buildResolverDir ->
            assertNull(buildResolverDir)
            if (embedded) {
                assert buildScriptFinder instanceof EmbeddedBuildScriptFinder
                assertEquals(expectedEmbeddedScript, buildScriptFinder.getBuildScript(null))
            } else {
                assert !(buildScriptFinder instanceof EmbeddedBuildScriptFinder)
                assertEquals(expectedBuildFileName, buildScriptFinder.buildFileName)
            }
            new Build()
        }
        buildMockFor.demand.newInstanceFactory(1..1) {File pluginProperties, File importsFile ->
            assertEquals(expectedGradleImportsFile, importsFile)
            assertEquals(new File(TEST_GRADLE_HOME, Main.DEFAULT_PLUGIN_PROPERTIES), pluginProperties)
            buildFactory
        }
        buildMockFor.demand.getSettingsProcessor(1..1) {
            settingsProcessor
        }
        if (embedded) {
            if (taskList) {
                buildMockFor.demand.taskListNonRecursivelyWithCurrentDirAsRoot(1..1) {StartParameter startParameter ->
                    try {
                        checkStartParameter(startParameter, true)
                    } catch (Throwable e) {
                        assertException = e
                    }
                }
            } else {
                buildMockFor.demand.runNonRecursivelyWithCurrentDirAsRoot(1..1) {StartParameter startParameter ->
                    try {
                        checkStartParameter(startParameter)
                    } catch (Throwable e) {
                        assertException = e
                    }
                }
            }
        } else {
            if (taskList) {
                buildMockFor.demand.taskList(1..1) {StartParameter startParameter ->
                    try {
                        checkStartParameter(startParameter, true)
                    } catch (Throwable e) {
                        assertException = e
                    }
                }
            } else {
                buildMockFor.demand.run(1..1) {StartParameter startParameter ->
                    try {
                        checkStartParameter(startParameter)
                    } catch (Throwable e) {
                        assertException = e
                    }
                }
            }
        }

        buildMockFor.use {
            mainCall.call()
        }
        assertNotNull(settingsProcessor.buildSourceBuilder)
        assertEquals(buildFactory, settingsProcessor.buildSourceBuilder.embeddedBuildExecuter.buildFactory)
        if (assertException) throw assertException
    }

    void testMainWithSpecifiedGradleUserHomeDirectory() {
        expectedGradleUserHome = HelperUtil.makeNewTestDir()
        checkMain {Main.main(args(["-Sg", expectedGradleUserHome.canonicalFile]) + expectedTaskNames as String[])}
    }

    void testMainWithSpecifiedExistingProjectDirectory() {
        expectedProjectDir = HelperUtil.makeNewTestDir()
        checkMain {Main.main(args(["-Sp", expectedProjectDir.canonicalFile]) + expectedTaskNames as String[])}
    }

    void testMainWithDisabledDefaultImports() {
        expectedGradleImportsFile = null
        checkMain {Main.main(args(["-SI"] + expectedTaskNames) as String[])}
    }

    void testMainWithSpecifiedBuildFileName() {
        expectedBuildFileName = 'somename'
        checkMain {Main.main(args(["-Sb", expectedBuildFileName] + expectedTaskNames) as String[])}
    }

    void testMainWithSystemProperties() {
        String prop1 = 'gradle.prop1'
        String valueProp1 = 'value1'
        String prop2 = 'gradle.prop2'
        String valueProp2 = 'value2'
        expectedSystemProperties = [(prop1): valueProp1, (prop2): valueProp2]
        checkMain {Main.main(args(["-D$prop1=$valueProp1", "-SD", "$prop2=$valueProp2"]) + expectedTaskNames as String[])}
    }

    void testMainWithStartProperties() {
        String prop1 = 'prop1'
        String valueProp1 = 'value1'
        String prop2 = 'prop2'
        String valueProp2 = 'value2'
        expectedProjectProperties = [(prop1): valueProp1, (prop2): valueProp2]
        checkMain {Main.main(args(["-SP$prop1=$valueProp1", "-P", "$prop2=$valueProp2"]) + expectedTaskNames as String[])}
    }

    void testMainWithNonRecursiveFlagSet() {
        expectedRecursive = false
        checkMain {Main.main(args(["-Sn"] + expectedTaskNames) as String[])}
    }

    void testMainWithSearchUpwardsFlagSet() {
        expectedSearchUpwards = false
        checkMain {Main.main(args(["-Su"] + expectedTaskNames) as String[])}
    }

    void testMainWithEmbeddedScript() {
        checkMain(true) {Main.main(args(["-Se", expectedEmbeddedScript]) + expectedTaskNames as String[])}
    }

    void testMainWithEmbeddedScriptAndConflictingOptions() {
        buildMockFor.use {
            Main.main(args(["-Se", "someScript", "-u", "clean"]) as String[])
            Main.main(args(["-Se", "someScript", "-n", "clean"]) as String[])
            Main.main(args(["-Se", "someScript", "-bsomeFile", "clean"]) as String[])
        }
    }

    void testMainWithShowTargets() {
        checkMain(false, true) {Main.main(args(["-St"]) as String[])}
    }

    void testMainWithShowTargetsAndEmbeddedScript() {
        checkMain(true, true) {Main.main(args(["-Se$expectedEmbeddedScript", "-t"]) as String[])}
    }

    void testMainWithPParameterWithoutArgument() {
        buildMockFor.use {
            Main.main(args(["-Sp"]) as String[])
        }
        // The projectLoaderMock throws an exception, if the main method does not return prematurely (what it should do).
    }

    void testMainWithMissingGradleHome() {
        System.properties.remove('gradle.home')
        buildMockFor.use {
            Main.main(args(["clean"]) as String[])
        }
        // Tests are run in one JVM. Therefore we need to set it again.
        System.properties['gradle.home'] = TEST_GRADLE_HOME
    }

    void testMainWithMissingTargets() {
        buildMockFor.use {
            Main.main(args([]) as String[])
        }
    }


    private List args(List args) {
        ['toolsinfo'] + args
    }

    //    void testMainWithException() {
    //        showProp()
    //        buildMockFor.demand.run {List taskNames, File currentDir, String buildFileName, boolean recursive, boolean searchUpwards ->
    //            throw new RuntimeException()
    //        }
    //        buildMockFor.use {
    //            Main.main(["clean", "compile"] as String[])
    //            // Getting here means the exception was caught. This is what we want to test.
    //        }
    //    }


}