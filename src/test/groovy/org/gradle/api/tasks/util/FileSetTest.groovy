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
import org.junit.Before
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class FileSetTest extends AbstractTestForPatternSet {
    FileSet fileSet

    File testDir

    PatternSet getPatternSet() {
        return fileSet
    }

    Class getPatternSetType() {
        FileSet
    }

    @Before public void setUp()  {
        super.setUp()
        testDir = '/testdir' as File
        fileSet = patternSetType.newInstance(testDir, contextObject)
    }

    @Test public void testFileSet() {
        assert fileSet.contextObject.is(contextObject)
        assert fileSet.dir.is(testDir)

        fileSet = new FileSet(testDir)
        assert fileSet.contextObject.is(fileSet)
        assert fileSet.dir.is(testDir)

        fileSet = new FileSet(dir: testDir)
        assert fileSet.contextObject.is(fileSet)
        assert fileSet.dir.is(testDir)
    }

    @Test(expected = InvalidUserDataException) public void testFileSetWithIllegalArgument() {
        new FileSet([:])
    }

    void checkPatternSetForAntBuilderTest(antPatternSet, PatternSet patternSet) {
        // Unfortunately, the ant fileset task has no public properties to check its includes/excludes values
        // todo: We might get hold of those properties via reflection. But this makes things unstable. As Ant
        // only guarantees statbility for its public API. 
        assertEquals(testDir, antPatternSet.dir)
    }

}
