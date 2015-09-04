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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.StopExecutionException
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GFileUtils
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes
import static org.hamcrest.Matchers.equalTo

@UsesNativeServices
public class DefaultSourceDirectorySetTest extends Specification {
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private final TestFile testDir = tmpDir.testDirectory
    private FileResolver resolver
    private DefaultSourceDirectorySet set

    public void setup() {
        resolver = {src -> src instanceof File ? src : new File(testDir, src as String)} as FileResolver
        set = new DefaultSourceDirectorySet('<display-name>', resolver)
    }

    public void addsResolvedSourceDirectory() {
        when:
        set.srcDir 'dir1'

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1')] as Set)
    }

    public void addsResolvedSourceDirectories() {
        when:
        set.srcDir {-> ['dir1', 'dir2'] }

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1'), new File(testDir, 'dir2')] as Set)
    }

    public void addsNestedDirectorySet() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('<nested>', resolver)
        nested.srcDir 'dir1'

        when:
        set.source nested

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1')] as Set)
    }

    public void settingSourceDirsReplacesExistingContent() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('<nested>', resolver)
        nested.srcDir 'ignore me'
        set.srcDir 'ignore me as well'
        set.source nested

        when:
        set.srcDirs = ['dir1', 'dir2']

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1'), new File(testDir, 'dir2')] as Set)
    }

    public void containsFilesFromEachSourceDirectory() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir/file2.txt', 'subdir2/file1.txt')
    }

    public void convertsSourceDirectoriesToDirectoryTrees() {
        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.include 'includes'
        set.exclude 'excludes'
        def trees = set.srcDirTrees as List

        then:
        trees.size() == 2
        trees[0].dir == testDir.file('dir1')
        trees[0].patterns.includes as List == ['includes']
        trees[0].patterns.excludes as List == ['excludes']
        trees[1].dir == testDir.file('dir2')
        trees[1].patterns.includes as List == ['includes']
        trees[1].patterns.excludes as List == ['excludes']
    }

    public void convertsNestedDirectorySetsToDirectoryTrees() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('<nested>', resolver)
        nested.srcDirs 'dir1', 'dir2'

        when:
        set.source nested
        def trees = set.srcDirTrees as List

        then:
        trees.size() == 2
        trees[0].dir == testDir.file('dir1')
        trees[1].dir == testDir.file('dir2')
    }

    public void removesDuplicateDirectoryTrees() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('<nested>', resolver)
        nested.srcDirs 'dir1', 'dir2'

        when:
        set.source nested
        set.srcDir 'dir1'
        def trees = set.srcDirTrees as List

        then:
        trees.size() == 2
        trees[0].dir == testDir.file('dir1')
        trees[1].dir == testDir.file('dir2')
    }

    public void canUsePatternsToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/ignored.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/file2.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/ignored.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.include '**/file*'
        set.exclude '**/file2*'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir2/file1.txt')
    }

    public void canUseFilterPatternsToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/ignored.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/file2.txt'))
        GFileUtils.touch(new File(srcDir2, 'subdir2/ignored.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.filter.include '**/file*'
        set.filter.exclude '**/file2*'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir2/file1.txt')
    }

    public void ignoresSourceDirectoriesWhichDoNotExist() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt')
    }

    public void failsWhenSourceDirectoryIsNotADirectory() {
        File srcDir = new File(testDir, 'dir1')
        GFileUtils.touch(srcDir)

        when:
        set.srcDir 'dir1'
        set.addToAntBuilder("node", "fileset")

        then:
        InvalidUserDataException e = thrown()
        e.message == "Source directory '$srcDir' is not a directory."
    }

    public void throwsStopExceptionWhenNoSourceDirectoriesExist() {
        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.stopExecutionIfEmpty()

        then:
        StopExecutionException e = thrown()
        e.message == '<display-name> does not contain any files.'
    }

    public void throwsStopExceptionWhenNoSourceDirectoryHasMatches() {
        when:
        set.srcDir 'dir1'
        File srcDir = new File(testDir, 'dir1')
        srcDir.mkdirs()
        set.stopExecutionIfEmpty()

        then:
        StopExecutionException e = thrown()
        e.message == '<display-name> does not contain any files.'
    }

    public void doesNotThrowStopExceptionWhenSomeSourceDirectoriesAreNotEmpty() {
        when:
        set.srcDir 'dir1'
        GFileUtils.touch(new File(testDir, 'dir1/file1.txt'))
        set.srcDir 'dir2'
        set.stopExecutionIfEmpty()

        then:
        notThrown(Throwable)
    }

    public void canUseMatchingMethodToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir2/file1.txt'))

        when:
        set.srcDir 'dir1'
        FileTree filteredSet = set.matching {
            include '**/file1.txt'
            exclude 'subdir2/**'
        }

        then:
        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')
    }

    public void canUsePatternsAndFilterPatternsAndMatchingMethodToFilterSourceFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.other'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/ignored.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir2/file1.txt'))

        when:
        set.srcDir 'dir1'
        set.include '**/*file?.*'
        set.filter.include '**/*.txt'
        FileTree filteredSet = set.matching {
            include 'subdir/**'
            exclude '**/file2.txt'
        }

        then:
        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')
    }

    public void filteredSetIsLive() {
        File srcDir1 = new File(testDir, 'dir1')
        GFileUtils.touch(new File(srcDir1, 'subdir/file1.txt'))
        GFileUtils.touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        GFileUtils.touch(new File(srcDir2, 'subdir2/file1.txt'))

        when:
        set.srcDir 'dir1'
        FileTree filteredSet = set.matching { include '**/file1.txt' }

        then:
        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')

        when:
        set.srcDir 'dir2'

        then:
        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt', 'subdir2/file1.txt')
    }
}

