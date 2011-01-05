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
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.StopExecutionException
import org.gradle.util.GFileUtils
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.api.tasks.AntBuilderAwareUtil.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*;

@RunWith (JMock)
public class DefaultSourceDirectorySetTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    private final File testDir = tmpDir.dir
    private FileResolver resolver
    private DefaultSourceDirectorySet set

    @Before
    public void setUp() {
        resolver = {src -> src instanceof File ? src : new File(testDir, src as String)} as FileResolver
        set = new DefaultSourceDirectorySet('<display-name>', resolver)
    }

    @Test
    public void addsResolvedSourceDirectoryToSet() {
        set.srcDir 'dir1'

        assertThat(set.srcDirs, equalTo([new File(testDir, 'dir1')] as Set))
    }

    @Test
    public void addsResolvedSourceDirectoriesToSet() {
        set.srcDir { -> ['dir1', 'dir2'] }

        assertThat(set.srcDirs, equalTo([new File(testDir, 'dir1'), new File(testDir, 'dir2')] as Set))
    }

    @Test
    public void canSetSourceDirectories() {
        set.srcDir 'ignore me'
        set.srcDirs = ['dir1', 'dir2']

        assertThat(set.srcDirs, equalTo([new File(testDir, 'dir1'), new File(testDir, 'dir2')] as Set))
    }

    @Test
    public void addsFilesetForEachSourceDirectory() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))

        set.srcDir 'dir1'
        set.srcDir 'dir2'

        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir/file2.txt', 'subdir2/file1.txt')
    }

    @Test
    public void canUsePatternsToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/ignored.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/file2.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/ignored.txt'))

        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.include '**/file*'
        set.exclude '**/file2*'

        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir2/file1.txt')
    }

    @Test
    public void canUseFilterPatternsToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/ignored.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/file2.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/ignored.txt'))

        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.filter.include '**/file*'
        set.filter.exclude '**/file2*'

        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir2/file1.txt')
    }

    @Test
    public void ignoresSourceDirectoriesWhichDoNotExist() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))

        set.srcDir 'dir1'
        set.srcDir 'dir2'

        assertSetContainsForAllTypes(set, 'subdir/file1.txt')
    }

    @Test
    public void failsWhenSourceDirectoryIsNotADirectory() {
        File srcDir = new File(testDir, 'dir1')
        GFileUtils.touch(srcDir)

        set.srcDir 'dir1'
        try {
            set.addToAntBuilder("node", "fileset")
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("Source directory '$srcDir' is not a directory." as String))
        }
    }

    @Test
    public void throwsStopExceptionWhenNoSourceDirectoriesExist() {
        set.srcDir 'dir1'
        set.srcDir 'dir2'

        try {
            set.stopExecutionIfEmpty()
            fail()
        } catch (StopExecutionException e) {
            assertThat(e.message, equalTo('<display-name> does not contain any files.'))
        }
    }

    @Test
    public void throwsStopExceptionWhenNoSourceDirectoryHasMatches() {
        set.srcDir 'dir1'
        File srcDir = new File(testDir, 'dir1')
        srcDir.mkdirs()

        try {
            set.stopExecutionIfEmpty()
            fail()
        } catch (StopExecutionException e) {
            assertThat(e.message, equalTo('<display-name> does not contain any files.'))
        }
    }

    @Test
    public void doesNotThrowStopExceptionWhenSomeSourceDirectoriesAreNotEmpty() {
        set.srcDir 'dir1'
        GFileUtils.touch(new File(testDir, 'dir1/file1.txt'))
        set.srcDir 'dir2'

        set.stopExecutionIfEmpty()
    }

    @Test
    public void canUseMatchingMethodToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir2/file1.txt'))

        set.srcDir 'dir1'

        FileTree filteredSet = set.matching {
            include '**/file1.txt'
            exclude 'subdir2/**'
        }

        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')
    }

    @Test
    public void canUsePatternsAndFilterPatternsAndMatchingMethodToFilterSourceFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.other'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/ignored.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir2/file1.txt'))

        set.srcDir 'dir1'
        set.include '**/*file?.*'
        set.filter.include '**/*.txt'

        FileTree filteredSet = set.matching {
            include 'subdir/**'
            exclude '**/file2.txt'
        }

        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')
    }

    @Test
    public void filteredSetIsLive() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))

        set.srcDir 'dir1'

        FileTree filteredSet = set.matching { include '**/file1.txt' }
        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')

        set.srcDir 'dir2'

        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt', 'subdir2/file1.txt')
    }
}

