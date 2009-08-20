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

package org.gradle.api.tasks.util

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.StopActionException
import org.gradle.util.HelperUtil
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class ExistingDirsFilterTest {
    ExistingDirsFilter testObj
    File existingDir, destDir, nonExistingDir
    List allDirs


    @Before public void setUp() {
        testObj = new ExistingDirsFilter()
        File root = HelperUtil.makeNewTestDir()
        existingDir = new File(root, 'dir1')
        destDir = new File(root, 'dir11')
        existingDir.mkdirs()
        nonExistingDir = new File(root, 'dir2')
        allDirs = [existingDir, nonExistingDir]
    }


    @Test public void testFindExistingDirs() {
        assertEquals([existingDir], testObj.findExistingDirs(allDirs))
        assertEquals([existingDir], testObj.findExistingDirs(allDirs))
    }

    @Test public void testFindExistingDirsAndThrowStopActionIfNoneWithExistingDir() {
        assertEquals([existingDir], testObj.findExistingDirsAndThrowStopActionIfNone(allDirs))
    }

    @Test (expected = StopActionException) public void testFindExistingDirsAndThrowStopActionIfNone() {
        testObj.findExistingDirsAndThrowStopActionIfNone([nonExistingDir])
    }

    @Test (expected = StopActionException) public void testCheckExistenceAndThrowStopActionIfNot() {
        testObj.checkExistenceAndThrowStopActionIfNot(existingDir)
        testObj.checkExistenceAndThrowStopActionIfNot(nonExistingDir)
    }

    @Test public void testCheckDestDirAndFindExistingDirsAndThrowStopActionIfNoneWithExistingDir() {
        assertEquals([existingDir], testObj.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, allDirs))
    }

    @Test (expected = StopActionException) public void testCheckDestDirAndFindExistingDirsAndThrowStopActionIfNoneNonExistingDir() {
        testObj.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, [nonExistingDir])
    }

    @Test (expected = InvalidUserDataException) public void testCheckDestDirAndFindExistingDirsAndThrowStopActionIfNoneWithNullDestinationDir() {
        testObj.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(null, allDirs)
    }

}
