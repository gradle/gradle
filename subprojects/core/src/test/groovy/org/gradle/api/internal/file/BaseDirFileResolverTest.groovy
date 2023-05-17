/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file

import org.gradle.api.InvalidUserDataException
import org.gradle.api.PathValidation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.PreconditionVerifier
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.concurrent.Callable

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail

class BaseDirFileResolverTest {
    static final String TEST_PATH = 'testpath'

    File baseDir
    File testFile
    File testDir

    BaseDirFileResolver baseDirConverter
    @Rule public TestNameTestDirectoryProvider rootDir = new TestNameTestDirectoryProvider(getClass())
    @Rule public PreconditionVerifier preconditions = new PreconditionVerifier()

    @Before public void setUp() {
        baseDir = rootDir.testDirectory
        baseDirConverter = new BaseDirFileResolver(baseDir)
        testFile = new File(baseDir, 'testfile')
        testDir = new File(baseDir, 'testdir')
    }

    @Test(expected = IllegalArgumentException) public void testWithNullPath() {
        baseDirConverter.resolve(null)
    }

    @Test public void testWithNoPathValidation() {
        // No exceptions means test has passed
        baseDirConverter.resolve(TEST_PATH)
        baseDirConverter.resolve(TEST_PATH, PathValidation.NONE)
    }

    @Test public void testPathValidationWithNonexistentFile() {
        try {
            baseDirConverter.resolve(testFile.name, PathValidation.FILE)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("File '$testFile.canonicalFile' does not exist.".toString()))
        }
    }

    @Test public void testPathValidationForFileWithDirectory() {
        testDir.mkdir()
        try {
            baseDirConverter.resolve(testDir.name, PathValidation.FILE)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("File '$testDir.canonicalFile' is not a file.".toString()))
        }
    }

    @Test public void testWithValidFile() {
        testFile.createNewFile()
        baseDirConverter.resolve(testFile.name, PathValidation.FILE)
    }

    @Test public void testPathValidationWithNonexistentDirectory() {
        try {
            baseDirConverter.resolve(testDir.name, PathValidation.DIRECTORY)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("Directory '$testDir.canonicalFile' does not exist.".toString()))
        }
    }

    @Test public void testPathValidationWithValidDirectory() {
        testDir.mkdir()
        baseDirConverter.resolve(testDir.name, PathValidation.DIRECTORY)
    }

    @Test public void testPathValidationForDirectoryWithFile() {
        testFile.createNewFile()
        try {
            baseDirConverter.resolve(testFile.name, PathValidation.DIRECTORY)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("Directory '$testFile.canonicalFile' is not a directory.".toString()))
        }
    }

    @Test public void testPathValidationForExistingDirAndFile() {
        testDir.mkdir()
        testFile.createNewFile()
        baseDirConverter.resolve(testDir.name, PathValidation.EXISTS)
        baseDirConverter.resolve(testFile.name, PathValidation.EXISTS)
    }

    @Test public void testExistsPathValidationWithNonexistentDir() {
        try {
            baseDirConverter.resolve(testDir.name, PathValidation.EXISTS)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("File '$testDir.canonicalFile' does not exist.".toString()))
        }
    }

    @Test public void testExistsPathValidationWithNonexistentFile() {
        try {
            baseDirConverter.resolve(testFile.name, PathValidation.EXISTS)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("File '$testFile.canonicalFile' does not exist.".toString()))
        }
    }

    @Test public void testResolveAbsolutePath() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.path))
    }

    @Test public void testResolveRelativePath() {
        String relativeFileName = "relative"
        assertEquals(new File(baseDir, relativeFileName), baseDirConverter.resolve(relativeFileName))
        assertEquals(new File(baseDir, relativeFileName), baseDirConverter.resolve(new StringBuffer(relativeFileName)))
        assertEquals(baseDir, baseDirConverter.resolve("."))
    }

    @Test public void testResolveFileWithAbsolutePath() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile))
    }

    @Test public void testResolveFileWithRelativePath() {
        File relativeFile = new File('relative')
        assertEquals(new File(baseDir, 'relative'), baseDirConverter.resolve(relativeFile))
    }

    @Test public void testResolveRelativeFileURIString() {
        assertEquals(new File(baseDir, 'relative'), baseDirConverter.resolve('file:relative'))
        assertEquals(new File(baseDir.parentFile, 'relative'), baseDirConverter.resolve('file:../relative'))
    }

    @Test public void testResolveAbsoluteFileURIString() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.toURI().toString()))
    }

    @Test public void testResolveAbsoluteFileURI() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.toURI()))
    }

    @Test public void testResolveAbsoluteFileURL() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.toURI().toURL()))
    }

    @Test public void testResolveFilePathWithURIEncodedAndReservedCharacters() {
        File absoluteFile = new File('white%20space').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.absolutePath))
        absoluteFile = new File('white space').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.absolutePath))
    }

    @Test public void testResolveURIStringWithEncodedAndReservedCharacters() {
        assertEquals(new File(baseDir, 'white space'), baseDirConverter.resolve('file:white%20space'))
        assertEquals(new File(baseDir, 'not%encoded'), baseDirConverter.resolve('file:not%encoded'))
        assertEquals(new File(baseDir, 'bad%1'), baseDirConverter.resolve('file:bad%1'))
        assertEquals(new File(baseDir, 'white space'), baseDirConverter.resolve('file:white space'))
    }

    @Test public void testResolveURIWithReservedCharacters() {
        File absoluteFile = new File('white space').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.toURI()))
    }

    @Test public void testResolveURLWithReservedCharacters() {
        File absoluteFile = new File('white space').canonicalFile
        assertEquals(absoluteFile, baseDirConverter.resolve(absoluteFile.toURI().toURL()))
    }

    @Test public void testCannotResolveNonFileURI() {
        try {
            baseDirConverter.resolve("http://www.gradle.org")
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo('Cannot convert URL \'http://www.gradle.org\' to a file.'))
        }
    }

    @Test public void testResolveClosure() {
        assertEquals(new File(baseDir, 'relative'), baseDirConverter.resolve({'relative'}))
    }

    @Test public void testResolveCallable() {
        assertEquals(new File(baseDir, 'relative'), baseDirConverter.resolve({'relative'} as Callable))
    }

    @Test public void testResolveNestedClosuresAndCallables() {
        Callable callable = {'relative'} as Callable
        Closure closure = {callable}
        assertEquals(new File(baseDir, 'relative'), baseDirConverter.resolve(closure))
    }

    @Test public void testResolveAbsolutePathToUri() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile.toURI(), baseDirConverter.resolveUri(absoluteFile.path))
    }

    @Test public void testResolveRelativePathToUri() {
        assertEquals(new File(baseDir, 'relative').toURI(), baseDirConverter.resolveUri('relative'))
    }

    @Test public void testResolveFileWithAbsolutePathToUri() {
        File absoluteFile = new File('nonRelative').canonicalFile
        assertEquals(absoluteFile.toURI(), baseDirConverter.resolveUri(absoluteFile))
    }

    @Test public void testResolveFileWithRelativePathToUri() {
        File relativeFile = new File('relative')
        assertEquals(new File(baseDir, 'relative').toURI(), baseDirConverter.resolveUri(relativeFile))
    }

    @Test public void testResolveUriStringToUri() {
        assertEquals(new URI("http://www.gradle.org"), baseDirConverter.resolveUri("http://www.gradle.org"))
    }

    @Test public void testResolveUriObjectToUri() {
        URI uri = new URI("http://www.gradle.org")
        assertEquals(uri, baseDirConverter.resolveUri(uri))
    }

    @Test public void testResolveUrlObjectToUri() {
        assertEquals(new URI("http://www.gradle.org"), baseDirConverter.resolveUri(new URL("http://www.gradle.org")))
    }

    @Test public void testResolveAbsolutePathWithReservedCharsToUri() {
        assertEquals(new File(baseDir, 'with white%20space').toURI(), baseDirConverter.resolveUri('with white%20space'))
        assertEquals('with white%20space', baseDirConverter.resolve(baseDirConverter.resolveUri('with white%20space')).name)
    }

    @Test public void testResolveUriStringWithEncodedCharsToUri() {
        assertEquals(new URI("http://www.gradle.org/white%20space"), baseDirConverter.resolveUri("http://www.gradle.org/white%20space"))
    }

    @Test public void testResolveRelativePathToRelativePath() {
        assertEquals("relative", baseDirConverter.resolveAsRelativePath("relative"))
        assertEquals("relative", baseDirConverter.resolveForDisplay("relative"))

        assertEquals("relative${File.separator}child".toString(), baseDirConverter.resolveAsRelativePath("relative/child"))
        assertEquals("relative${File.separator}child".toString(), baseDirConverter.resolveForDisplay("relative/child"))
    }

    @Test public void testResolveAbsoluteChildPathToRelativePath() {
        def absoluteFile = new File(baseDir, 'child').absoluteFile
        assertEquals('child', baseDirConverter.resolveAsRelativePath(absoluteFile))
        assertEquals('child', baseDirConverter.resolveAsRelativePath(absoluteFile.absolutePath))

        assertEquals('child', baseDirConverter.resolveForDisplay(absoluteFile))

        def absoluteNestedFile = new File(baseDir, 'child/nested').absoluteFile
        assertEquals("child${File.separator}nested".toString(), baseDirConverter.resolveAsRelativePath(absoluteNestedFile))
        assertEquals("child${File.separator}nested".toString(), baseDirConverter.resolveAsRelativePath(absoluteNestedFile.absolutePath))

        assertEquals("child${File.separator}nested".toString(), baseDirConverter.resolveForDisplay(absoluteNestedFile))
    }

    @Test public void testResolveAbsoluteSiblingPathToRelativePath() {
        def absoluteFile = new File(baseDir, '../sibling').absoluteFile
        assertEquals("..${File.separator}sibling".toString(), baseDirConverter.resolveAsRelativePath(absoluteFile))
        assertEquals("..${File.separator}sibling".toString(), baseDirConverter.resolveAsRelativePath(absoluteFile.absolutePath))

        assertEquals("..${File.separator}sibling".toString(), baseDirConverter.resolveForDisplay(absoluteFile))

        def absoluteNestedFile = new File(baseDir, '../sibling/nested').absoluteFile
        assertEquals("..${File.separator}sibling${File.separator}nested".toString(), baseDirConverter.resolveAsRelativePath(absoluteNestedFile))
        assertEquals("..${File.separator}sibling${File.separator}nested".toString(), baseDirConverter.resolveAsRelativePath(absoluteNestedFile.absolutePath))

        assertEquals("..${File.separator}sibling${File.separator}nested".toString(), baseDirConverter.resolveForDisplay(absoluteNestedFile))
    }

    @Test public void testResolveBaseDirToRelativePath() {
        assertEquals('.', baseDirConverter.resolveAsRelativePath(baseDir))
        assertEquals('.', baseDirConverter.resolveAsRelativePath(baseDir.absolutePath))
        assertEquals('.', baseDirConverter.resolveAsRelativePath('.'))
        assertEquals('.', baseDirConverter.resolveAsRelativePath("../$baseDir.name"))

        assertEquals('.', baseDirConverter.resolveForDisplay(baseDir))
    }

    @Test public void testResolveParentDirToRelativePath() {
        assertEquals('..', baseDirConverter.resolveAsRelativePath(baseDir.parentFile))
        assertEquals('..', baseDirConverter.resolveAsRelativePath('..'))

        assertEquals('..', baseDirConverter.resolveForDisplay(baseDir.parentFile))
    }

    @Test public void testAncestorToRelativePath() {
        def ancestor = baseDir.parentFile.parentFile
        assertEquals("..${File.separator}..".toString(), baseDirConverter.resolveAsRelativePath(ancestor))
        assertEquals(ancestor.path, baseDirConverter.resolveForDisplay(ancestor))

        def nested = new File(baseDir.parentFile.parentFile, "file")
        assertEquals("..${File.separator}..${File.separator}file".toString(), baseDirConverter.resolveAsRelativePath(nested))
        assertEquals(nested.path, baseDirConverter.resolveForDisplay(nested))
    }

    @Test public void testCreateFileResolver() {
        File newBaseDir = new File(baseDir, 'subdir')
        assertEquals(new File(newBaseDir, 'file'), baseDirConverter.withBaseDir('subdir').resolve('file'))
    }
}
