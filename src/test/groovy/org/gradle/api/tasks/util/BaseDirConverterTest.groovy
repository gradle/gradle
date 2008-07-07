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
import org.junit.Before
import static org.junit.Assert.*
import org.junit.Test
import org.junit.After;


/**
 * @author Hans Dockter
 */
class BaseDirConverterTest {
    static final String TEST_PATH = 'testpath'

    File baseDir
    File rootDir
    File testFile
    File testDir

    BaseDirConverter baseDirConverter

    @Before public void setUp() {
        baseDirConverter = new BaseDirConverter()
        rootDir = HelperUtil.makeNewTestDir()
        baseDir = new File(rootDir, 'basedir')
        baseDir.mkdir()
        testFile = new File(baseDir, 'testfile')
        testDir = new File(baseDir, 'testdir')
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    @Test (expected = InvalidUserDataException) public void testWithNullPath() {
        baseDirConverter.baseDir(null, testFile)
    }

    @Test (expected = InvalidUserDataException) public void testWithNullBaseDir() {
        baseDirConverter.baseDir('somepath', null)
    }

    @Test public void testWithNoPathValidation() {
        // No exceptions means test has passed
        baseDirConverter.baseDir(TEST_PATH, testFile)
        baseDirConverter.baseDir(TEST_PATH, testFile, PathValidation.NONE)
    }

    @Test (expected = InvalidUserDataException) public void testPathValidationWithNonExistingFile() {
        baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.FILE)
    }

    @Test (expected = InvalidUserDataException) public void testPathValidationForFileWithDirectory() {
        testDir.mkdir()
        baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.FILE)
    }

    @Test public void testWithValidFile() {
        testFile.createNewFile()
        baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.FILE)
    }

    @Test (expected = InvalidUserDataException) public void testPathValidationWithNonExistingDirectory() {
        baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.DIRECTORY)
    }

    @Test public void testPathValidationWithValidDirectory() {
        testDir.mkdir()
        baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.DIRECTORY)
    }

    @Test (expected = InvalidUserDataException) public void testPathValidationForDirectoryWithFile() {
        testFile.createNewFile()
        baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.DIRECTORY)
    }

    @Test public void testPathValidationForExistingDirAndFile() {
        testDir.mkdir()
        testFile.createNewFile()
        baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.EXISTS)
        baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.EXISTS)
    }

    @Test(expected = InvalidUserDataException) public void testExistsPathValidationWithNonExistingDir() {
            baseDirConverter.baseDir(testDir.name, baseDir, PathValidation.EXISTS)
    }

    @Test(expected = InvalidUserDataException) public void testExistsPathValidationWithNonExistingFile() {
            baseDirConverter.baseDir(testFile.name, baseDir, PathValidation.EXISTS)
    }

    @Test public void testWithAbsolutePath() {
        File absoluteFile = new File('nonRelative').absoluteFile
        assertEquals(absoluteFile,
                baseDirConverter.baseDir(absoluteFile.path, baseDir))
    }


    @Test public void testWithRelativePath() {
        String relativeFileName = "relative"
        assertEquals(new File(baseDir, relativeFileName), baseDirConverter.baseDir(relativeFileName, baseDir))
    }
}
