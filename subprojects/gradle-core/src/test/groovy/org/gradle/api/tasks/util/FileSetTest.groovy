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
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.gradle.util.HelperUtil
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.UnionFileTree
import static org.gradle.api.tasks.AntBuilderAwareUtil.*

/**
 * @author Hans Dockter
 */
class FileSetTest extends AbstractTestForPatternSet {
    FileSet fileSet

    File testDir

    PatternFilterable getPatternSet() {
        return fileSet
    }

    Class getPatternSetType() {
        FileSet
    }

    public Map getConstructorMap() {
        [dir: testDir]
    }

    @Before public void setUp()  {
        super.setUp()
        testDir = HelperUtil.makeNewTestDir()
        fileSet = patternSetType.newInstance(testDir)
    }

    @Test public void testFileSetConstruction() {
        fileSet = new FileSet(testDir)
        assertEquals(testDir, fileSet.dir)

        fileSet = new FileSet(dir: testDir)
        assertEquals(testDir, fileSet.dir)
    }

    @Test(expected = InvalidUserDataException) public void testFileSetConstructionWithNoBaseDirSpecified() {
        FileSet fileSet = new FileSet([:])
        fileSet.matching {}
    }

    @Test public void testFileSetConstructionWithDirAsString() {
        FileSet fileSet = new FileSet(dir: 'dirname')
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
        assertSetContains(fileSet, 'subDir/included1', 'subDir2/included2')
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
        assertSetContains(fileSet, 'subDir/included1', 'subDir2/included2')
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
        assertSetContains(filtered, 'subDir/included1', 'subDir2/included2')
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
        assertSetContains(filtered, 'subDir/included1', 'subDir2/included2')
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
        assertSetContains(filtered, 'subDir/included1', 'subDir2/included2')
    }

    @Test public void testCanAddFileSets() {
        FileTree other = new FileSet(testDir)
        FileTree sum = fileSet + other
        assertThat(sum, instanceOf(UnionFileTree))
        assertThat(sum.sourceCollections, equalTo([fileSet, other] as Set))
    }

    @Test public void testDisplayName() {
        assertThat(fileSet.displayName, equalTo("file set '$testDir'".toString()))
    }
    
    void checkPatternSetForAntBuilderTest(antPatternSet, PatternSet patternSet) {
        // Unfortunately, the ant fileset task has no public properties to check its includes/excludes values
        // todo: We might get hold of those properties via reflection. But this makes things unstable. As Ant
        // only guarantees stability for its public API.
        assertEquals(testDir, antPatternSet.dir)
    }

}
