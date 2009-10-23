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
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.tasks.StopExecutionException
import org.gradle.util.TemporaryFolder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.gradle.api.file.FileVisitorUtil.*
import static org.gradle.api.tasks.AntBuilderAwareUtil.*
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class FileSetTest extends AbstractTestForPatternSet {
    FileSet fileSet
    FileResolver resolver = [resolve: {it as File}] as FileResolver
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
    File testDir = tmpDir.dir

    PatternFilterable getPatternSet() {
        return fileSet
    }

    Class getPatternSetType() {
        FileSet
    }

    @Before public void setUp()  {
        super.setUp()
        fileSet = patternSetType.newInstance(testDir, resolver)
    }

    @Test public void testFileSetConstructionWithBaseDir() {
        fileSet = new FileSet(testDir, resolver)
        assertEquals(testDir, fileSet.dir)
    }

    @Test public void testFileSetConstructionFromMap() {
        fileSet = new FileSet(resolver, dir: testDir, includes: ['include'])
        assertEquals(testDir, fileSet.dir)
        assertEquals(['include'] as Set, fileSet.includes)
    }

    @Test(expected = InvalidUserDataException) public void testFileSetConstructionWithNoBaseDirSpecified() {
        FileSet fileSet = new FileSet([:], resolver)
        fileSet.matching {}
    }

    @Test public void testFileSetConstructionWithBaseDirAsString() {
        FileSet fileSet = new FileSet(resolver, dir: 'dirname')
        assertEquals(new File('dirname'), fileSet.dir);
    }

    @Test public void testCanScanForFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertThat(fileSet.files, equalTo([included1, included2] as Set))
    }

    @Test public void testCanVisitFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
    }

    @Test public void testCanStopVisitingFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir/otherDir/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertCanStopVisiting(fileSet)
    }

    @Test public void testContainsFiles() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertTrue(fileSet.contains(included1))
        assertTrue(fileSet.contains(included2))
        assertFalse(fileSet.contains(testDir))
        assertFalse(fileSet.contains(included1.parentFile))
        assertFalse(fileSet.contains(included2.parentFile))
        assertFalse(fileSet.contains(new File(testDir, 'does not exist')))
        assertFalse(fileSet.contains(testDir.parentFile))
        assertFalse(fileSet.contains(new File('something')))
    }

    @Test public void testCanAddToAntTask() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        [included1, included2].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
    }

    @Test public void testIsEmptyWhenBaseDirDoesNotExist() {
        fileSet.dir = new File(testDir, 'does not exist')

        assertThat(fileSet.files, isEmpty())
        assertSetContainsForAllTypes(fileSet)
        assertVisits(fileSet, [], [])
    }

    @Test public void testCanLimitFilesUsingPatterns() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        fileSet.include('*/*included*')
        fileSet.exclude('**/not*')

        assertThat(fileSet.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(fileSet, 'subDir/included1', 'subDir2/included2')
        assertVisits(fileSet, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(fileSet.contains(included1))
        assertFalse(fileSet.contains(excluded1))
        assertFalse(fileSet.contains(ignored1))
    }

    @Test public void testCanFilterFileSetUsingConfigureClosure() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        FileSet filtered = fileSet.matching {
            include('*/*included*')
            exclude('**/not*')
        }

        assertThat(filtered.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(filtered.contains(included1))
        assertFalse(filtered.contains(excluded1))
        assertFalse(filtered.contains(ignored1))
    }
    
    @Test public void testCanFilterFileSetUsingPatternSet() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        PatternSet patternSet = new PatternSet(includes: ['*/*included*'], excludes: ['**/not*'])
        FileSet filtered = fileSet.matching(patternSet)

        assertThat(filtered.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(filtered.contains(included1))
        assertFalse(filtered.contains(excluded1))
        assertFalse(filtered.contains(ignored1))
    }
    
    @Test public void testCanFilterAndLimitFileSet() {
        File included1 = new File(testDir, 'subDir/included1')
        File included2 = new File(testDir, 'subDir2/included2')
        File excluded1 = new File(testDir, 'subDir/notincluded')
        File excluded2 = new File(testDir, 'subDir/excluded')
        File ignored1 = new File(testDir, 'ignored')
        [included1, included2, excluded1, excluded2, ignored1].each {File file ->
            file.parentFile.mkdirs()
            file.text = 'some text'
        }

        fileSet.exclude '**/excluded*'

        FileSet filtered = fileSet.matching {
            include('*/*included*')
            exclude('**/not*')
        }

        assertThat(filtered.files, equalTo([included1, included2] as Set))
        assertSetContainsForAllTypes(filtered, 'subDir/included1', 'subDir2/included2')
        assertVisits(filtered, ['subDir/included1', 'subDir2/included2'], ['subDir', 'subDir2'])
        assertTrue(filtered.contains(included1))
        assertFalse(filtered.contains(excluded1))
        assertFalse(filtered.contains(ignored1))
    }

    @Test public void testCanAddFileSetsTogether() {
        FileTree other = new FileSet(testDir, resolver)
        FileTree sum = fileSet + other
        assertThat(sum, instanceOf(UnionFileTree))
        assertThat(sum.sourceCollections, equalTo([fileSet, other]))
    }

    @Test public void testDisplayName() {
        assertThat(fileSet.displayName, equalTo("file set '$testDir'".toString()))
    }

    @Test public void testStopExecutionIfEmptyWhenNoMatchingFilesFound() {
        fileSet.include('**/*included')
        new File(testDir, 'excluded').text = 'some text'

        try {
            fileSet.stopExecutionIfEmpty()
            fail()
        } catch (StopExecutionException e) {
            assertThat(e.message, equalTo("File set '$testDir' does not contain any files." as String))
        }
    }
    
    @Test public void testStopExecutionIfEmptyWhenMatchingFilesFound() {
        fileSet.include('**/*included')
        new File(testDir, 'included').text = 'some text'

        fileSet.stopExecutionIfEmpty()
    }

    void checkPatternSetForAntBuilderTest(antPatternSet, PatternSet patternSet) {
        // Unfortunately, the ant fileset task has no public properties to check its includes/excludes values
        // todo: We might get hold of those properties via reflection. But this makes things unstable. As Ant
        // only guarantees stability for its public API.
        assertEquals(testDir, antPatternSet.dir)
    }

}
