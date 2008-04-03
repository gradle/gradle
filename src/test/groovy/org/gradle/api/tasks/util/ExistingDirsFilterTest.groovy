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

import org.gradle.util.HelperUtil
import org.gradle.api.tasks.StopActionException
import org.gradle.api.InvalidUserDataException

/**
 * @author Hans Dockter
 */
class ExistingDirsFilterTest extends GroovyTestCase {
    ExistingDirsFilter testObj
    File existingDir, destDir, nonExistingDir
    List allDirs


    void setUp() {
        super.setUp();
        testObj = new ExistingDirsFilter()
        File root = HelperUtil.makeNewTestDir()
        existingDir = new File(root, 'dir1')
        destDir = new File(root, 'dir11')
        existingDir.mkdirs()
        nonExistingDir = new File(root, 'dir2')
        allDirs = [existingDir, nonExistingDir]
    }


    void testFindExistingDirs() {
        assertEquals([existingDir], testObj.findExistingDirs(allDirs))
        assertEquals([existingDir], testObj.findExistingDirs(allDirs))
    }

    void testFindExistingDirsAndThrowStopActionIfNone() {
        assertEquals([existingDir], testObj.findExistingDirsAndThrowStopActionIfNone(allDirs))
        shouldFail(StopActionException) {
            testObj.findExistingDirsAndThrowStopActionIfNone([nonExistingDir])
        }
        assertEquals([existingDir], testObj.findExistingDirsAndThrowStopActionIfNone(allDirs))
    }   

    void testCheckExistenceAndThrowStopActionIfNot() {
        testObj.checkExistenceAndThrowStopActionIfNot(existingDir)
        shouldFail(StopActionException) {
            testObj.checkExistenceAndThrowStopActionIfNot(nonExistingDir)
        }
    }

    void testCheckDestDirAndFindExistingDirsAndThrowStopActionIfNone() {
        assertEquals([existingDir], testObj.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, allDirs))
        shouldFail(StopActionException) {
            testObj.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, [nonExistingDir])
        }
        shouldFail(InvalidUserDataException) {
            testObj.checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(null, allDirs)
        }
    }

}
