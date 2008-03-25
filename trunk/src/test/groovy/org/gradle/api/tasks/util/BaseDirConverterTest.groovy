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

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.InvalidUserDataException
import org.gradle.api.PathValidation
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
*/
class BaseDirConverterTest extends GroovyTestCase {
    static final String TEST_PATH = 'testpath'

    File baseDir
    File rootDir
    File testFile
    File testDir

    BaseDirConverter baseDirConverter

    void setUp() {
        baseDirConverter = new BaseDirConverter()
        rootDir = HelperUtil.makeNewTestDir()
        baseDir = new File(rootDir, 'basedir')
        baseDir.mkdir()
        testFile = new File(baseDir, 'testfile')
        testDir = new File(baseDir, 'testdir')
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testWithInvalidArguments() {
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(null, testFile)
        }
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir('somepath', null)
        }
    }

    void testWithNoPathValidation() {
        // No exceptions means test has passed
        baseDirConverter.baseDir(TEST_PATH, testFile)
        baseDirConverter.baseDir(TEST_PATH, testFile, PathValidation.NONE)
    }

    void testWithFilePathValidation() {
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.FILE)
        }
        testFile.createNewFile()
        baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.FILE)
        testDir.mkdir()
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.FILE)
        }
    }

    void testWithDirectoryPathValidation() {
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.DIRECTORY)
        }
        testDir.mkdir()
        baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.DIRECTORY)
        testFile.createNewFile()
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.DIRECTORY)
        }
    }

    void testWithExistsPathValidation() {
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.EXISTS)
        }
        shouldFail(InvalidUserDataException) {
            baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.EXISTS)
        }
        testDir.mkdir()
        testFile.createNewFile()
        baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.EXISTS)
        baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.EXISTS)
    }

    void testWithAbsolutePath() {
        File absoluteFile
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            absoluteFile = new File("c:/absolute")
        } else {
            absoluteFile = new File("/absolute")
        }
        assertEquals(absoluteFile,
                baseDirConverter.baseDir(absoluteFile.path, baseDir))
    }


    void testWithRelativePath() {
        String relativeFileName = "relative"
        assertEquals(new File(baseDir, relativeFileName), baseDirConverter.baseDir(relativeFileName, baseDir))
    }
}
