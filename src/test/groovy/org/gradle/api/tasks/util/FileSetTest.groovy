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
import org.gradle.api.internal.file.UnionFileTree;

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
        new FileSet([:])
    }

    @Test public void testFileSetConstructionWithDirAsString() {
        FileSet fileSet = new FileSet(dir: 'dirname')
        assertEquals(new File('dirname'), fileSet.dir);
    }

    @Test public void testCanScanForFiles() {
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

        Set f1 = fileSet.files
        Set f2 = [included1, included2] as Set
        assertThat(f1, equalTo(f2))
    }

    @Test public void testCanFilterFileSet() {
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

        Set f1 = filtered.files
        Set f2 = [included1, included2] as Set
        assertThat(f1, equalTo(f2))
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
