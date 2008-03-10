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
import org.gradle.api.internal.project.BuildScriptProcessor
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
* todo write disabled test 'testMainWithException' as integration test
*/
class MainTest extends GroovyTestCase {
    final static String TEST_DIR_NAME = "/testdir"

    StubFor fileStub
    MockFor buildMockFor

    void setUp() {
        fileStub = new StubFor(File)
        buildMockFor = new MockFor(Build)
    }

    void testMainWithSpecifiedNonExistingProjectDirectory() {
        fileStub.demand.getCanonicalFile {new File(TEST_DIR_NAME)}
        fileStub.demand.isDirectory {false}
        buildMockFor.use {
            fileStub.use {
                Main.main(["-p", TEST_DIR_NAME] as String[])
            }
        }
        // The buildMockFor throws an exception, if the main method does not return prematurely (what it should do). 
    }

    void testMainWithoutAnyOptions() {
        Throwable assertException = null

        buildMockFor.demand.run(1..1) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(["clean", "compile"], taskNames)
                assertEquals(new File("").canonicalFile, currentDir)
                assertEquals(new File(Main.DEFAULT_GRADLE_USER_HOME).canonicalFile, gradleUserHome)
                assertTrue(recursive)
                assertEquals(BuildScriptProcessor.DEFAULT_PROJECT_FILE, buildFileName)
                assertTrue(searchUpwards)
            } catch (Throwable e) {
                assertException = e
            }
        }

        buildMockFor.use {
            Main.main(["clean", "compile"] as String[])
        }
        if (assertException) throw assertException
    }

    void testMainWithSpecifiedGradleUserHomeDirectory() {
        Throwable assertException = null

        File expectedGradleUserHomeDir = HelperUtil.makeNewTestDir()
        buildMockFor.demand.run(1..1) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(["clean"], taskNames)
                assertEquals(expectedGradleUserHomeDir.canonicalFile, gradleUserHome)
                assertTrue(recursive)
                assertEquals(BuildScriptProcessor.DEFAULT_PROJECT_FILE, buildFileName)
                assertTrue(searchUpwards)
            } catch (Throwable e) {
                assertException = e
            }
        }
        buildMockFor.use {
            Main.main(["-g", expectedGradleUserHomeDir.canonicalFile, "clean"] as String[])
        }
        if (assertException) throw assertException
    }

    void testMainWithSpecifiedExistingProjectDirectory() {
        Throwable assertException = null

        File expectedProjectDir = HelperUtil.makeNewTestDir()
        buildMockFor.demand.run(1..1) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(["clean"], taskNames)
                assertEquals(expectedProjectDir.canonicalFile, currentDir)
                assertTrue(recursive)
                assertEquals(BuildScriptProcessor.DEFAULT_PROJECT_FILE, buildFileName)
                assertTrue(searchUpwards)
            } catch (Throwable e) {
                assertException = e
            }
        }
        buildMockFor.use {
            Main.main(["-p", expectedProjectDir.canonicalFile, "clean"] as String[])
        }
        if (assertException) throw assertException
    }

    void testMainWithSystemProperties() {
        String prop1 = 'gradle.prop1'
        String valueProp1 = 'value1'
        String prop2 = 'gradle.prop2'
        String valueProp2 = 'value2'
        Throwable assertException = null

        File expectedProjectDir = HelperUtil.makeNewTestDir()
        buildMockFor.demand.run(1..1) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(System.properties[prop1], valueProp1)
                assertEquals(System.properties[prop2], valueProp2)
            } catch (Throwable e) {
                assertException = e
            }
        }
        buildMockFor.use {
            Main.main(["-D$prop1=$valueProp1", "-D", "$prop2=$valueProp2", "-p", expectedProjectDir.canonicalFile, "clean"] as String[])
        }
        if (assertException) throw assertException
    }


    void testMainWithNonRecursiveFlagSet() {
        Throwable assertException = null

        buildMockFor.demand.run(1..1) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(["clean"], taskNames)
                assertEquals(new File("").canonicalFile, currentDir)
                assertFalse(recursive)
                assertEquals(BuildScriptProcessor.DEFAULT_PROJECT_FILE, buildFileName)
                assertTrue(searchUpwards)
            } catch (Throwable e) {
                assertException = e
            }
        }

        buildMockFor.use {
            Main.main(["-n", "clean"] as String[])
        }
        if (assertException) throw assertException
    }

    void testMainWithoSearchUpwardsFlagSet() {
        Throwable assertException = null

        buildMockFor.demand.run(1..1) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(["clean"], taskNames)
                assertEquals(new File("").canonicalFile, currentDir)
                assertTrue(recursive)
                assertEquals(BuildScriptProcessor.DEFAULT_PROJECT_FILE, buildFileName)
                assertFalse(searchUpwards)
            } catch (Throwable e) {
                assertException = e
            }
        }

        buildMockFor.use {
            Main.main(["-u", "clean"] as String[])
        }
        if (assertException) throw assertException
    }

    void testMainWithShowTargets() {
        Throwable assertException = null

        buildMockFor.demand.taskList(1..1) {File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
            try {
                assertEquals(new File("").canonicalFile, currentDir)
                assertTrue(recursive)
                assertEquals(BuildScriptProcessor.DEFAULT_PROJECT_FILE, buildFileName)
                assertTrue(searchUpwards)
            } catch (Throwable e) {
                assertException = e
            }
        }

        buildMockFor.use {
            Main.main(["-t"] as String[])
        }
        if (assertException) throw assertException
    }

    void testMainWithPParameterWithoutArgument() {
        buildMockFor.use {
            Main.main(["-p"] as String[])
        }
        // The projectLoaderMock throws an exception, if the main method does not return prematurely (what it should do).
    }

    void testMainWithMissingTargets() {
        buildMockFor.demand.run(0..0) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
        }
        buildMockFor.use {
            Main.main([] as String[])
        }
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

    void testMainWithMissingGradleHome() {
        System.properties.remove('gradle.home')
        buildMockFor.demand.run(0..0) {List taskNames, File currentDir, File gradleUserHome, String buildFileName, boolean recursive, boolean searchUpwards ->
        }
        buildMockFor.use {
            Main.main(["clean"] as String[])
        }
    }



}