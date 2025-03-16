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

import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Function

import static org.apache.commons.io.FileUtils.touch
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes
import static org.hamcrest.CoreMatchers.equalTo

class DefaultSourceDirectorySetTest extends Specification {
    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    private final TestFile testDir = tmpDir.testDirectory
    private FileCollectionFactory fileCollectionFactory = TestFiles.fileCollectionFactory(testDir)
    private TaskDependencyFactory taskDependencyFactory = TestFiles.taskDependencyFactory()
    private DirectoryFileTreeFactory directoryFileTreeFactory = TestFiles.directoryFileTreeFactory()
    private ObjectFactory objectFactory = TestUtil.objectFactory()
    private org.gradle.internal.Factory<PatternSet> patternSetFactory = TestFiles.patternSetFactory
    private DefaultSourceDirectorySet set

    void setup() {
        set = new DefaultSourceDirectorySet('files', '<display-name>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
    }

    void hasUsefulToString() {
        expect:
        set.displayName == '<display-name>'
        set.toString() == '<display-name>'
    }

    void viewsHaveSameDisplayNameAsSet() {
        expect:
        set.sourceDirectories.toString() == '<display-name>'
        set.asFileTree.toString() == '<display-name>'
        set.matching {}.toString() == '<display-name>'
    }

    void isEmptyWhenNoSourceDirectoriesSpecified() {
        expect:
        set.empty
        set.files.empty
        set.sourceDirectories.empty
        set.srcDirs.empty
        set.srcDirTrees.empty
    }

    void addsResolvedSourceDirectory() {
        when:
        set.srcDir 'dir1'

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1')] as Set)
    }

    void addsResolvedSourceDirectories() {
        when:
        set.srcDir {-> ['dir1', 'dir2'] }

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1'), new File(testDir, 'dir2')] as Set)
    }

    void addsContentsOfAnotherSourceDirectorySet() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('nested', '<nested>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
        nested.srcDir 'dir1'

        when:
        set.source nested

        then:
        set.srcDirs == [testDir.file('dir1')] as Set

        when:
        nested.srcDir 'dir2'

        then:
        set.srcDirs == [testDir.file('dir1'), testDir.file('dir2')] as Set
    }

    void addsSourceDirectoriesOfAnotherSourceDirectorySet() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('nested', '<nested>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
        nested.srcDir 'dir1'

        when:
        set.srcDirs nested.sourceDirectories

        then:
        set.srcDirs == [testDir.file('dir1')] as Set

        when:
        nested.srcDir 'dir2'

        then:
        set.srcDirs == [testDir.file('dir1'), testDir.file('dir2')] as Set
    }

    void settingSourceDirsReplacesExistingContent() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('nested', '<nested>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
        nested.srcDir 'ignore me'
        set.srcDir 'ignore me as well'
        set.source nested

        when:
        set.srcDirs = ['dir1', 'dir2']

        then:
        set.srcDirs equalTo([new File(testDir, 'dir1'), new File(testDir, 'dir2')] as Set)
    }

    void canViewSourceDirectoriesAsLiveFileCollection() {
        when:
        def dirs = set.sourceDirectories
        set.srcDir 'dir1'

        then:
        dirs.files == [testDir.file('dir1')] as Set

        when:
        set.srcDir 'dir2'

        then:
        dirs.files == [testDir.file('dir1'), testDir.file('dir2')] as Set

        when:
        set.srcDirs = []

        then:
        dirs.files.empty
    }

    void containsFilesFromEachSourceDirectory() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        touch(new File(srcDir2, 'subdir2/file1.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir/file2.txt', 'subdir2/file1.txt')
    }

    void convertsSourceDirectoriesToDirectoryTrees() {
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

    void convertsNestedDirectorySetsToDirectoryTrees() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('nested', '<nested>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
        nested.srcDirs 'dir1', 'dir2'

        when:
        set.source nested
        def trees = set.srcDirTrees as List

        then:
        trees.size() == 2
        trees[0].dir == testDir.file('dir1')
        trees[1].dir == testDir.file('dir2')
    }

    void removesDuplicateDirectoryTrees() {
        SourceDirectorySet nested = new DefaultSourceDirectorySet('nested', '<nested>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
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

    void canUsePatternsToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        touch(new File(srcDir1, 'subdir/ignored.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        touch(new File(srcDir2, 'subdir2/file1.txt'))
        touch(new File(srcDir2, 'subdir2/file2.txt'))
        touch(new File(srcDir2, 'subdir2/ignored.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.include '**/file*'
        set.exclude '**/file2*'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir2/file1.txt')
    }

    void "can use patterns to filter files but not directories with getSrcDirTrees method"() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        touch(new File(srcDir2, 'subdir2/file1.txt'))
        touch(new File(srcDir2, 'subdir2/file2.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.exclude 'dir1' // Dirs aren't filtered
        set.exclude '**/file1.txt' // Files are filtered

        then:
        def trees = set.getSrcDirTrees()
        trees.size() == 2
        trees.collect { it.getDir() } == [srcDir1, srcDir2] // Dirs aren't filtered
        set.getSrcDirs() == [srcDir1, srcDir2] as Set

        DirectoryFileTree tree1 = set.getSrcDirTrees().first() as DirectoryFileTree
        tree1.getDir() == srcDir1
        assertSetContainsForAllTypes(tree1, 'subdir/file2.txt')

        DirectoryFileTree tree2 = set.getSrcDirTrees().last() as DirectoryFileTree
        tree2.getDir() == srcDir2
        assertSetContainsForAllTypes(tree2, 'subdir2/file2.txt')
    }

    void "can use patterns to include only certain files but not directories with getSrcDirTrees method"() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir2/file2.txt'))
        touch(new File(srcDir1, 'subdir3/file3.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        touch(new File(srcDir2, 'subdir/file1.txt'))
        touch(new File(srcDir2, 'subdir2/file2.txt'))
        touch(new File(srcDir1, 'subdir3/file3.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.include'**/subdir2/**' // Only include subdir2

        then:
        def trees = set.getSrcDirTrees()
        trees.size() == 2
        trees.collect { it.getDir() } == [srcDir1, srcDir2] // Dirs aren't filtered
        set.getSrcDirs() == [srcDir1, srcDir2] as Set

        DirectoryFileTree tree1 = set.getSrcDirTrees().first() as DirectoryFileTree
        tree1.getDir() == srcDir1
        assertSetContainsForAllTypes(tree1, 'subdir2/file2.txt')

        DirectoryFileTree tree2 = set.getSrcDirTrees().last() as DirectoryFileTree
        tree2.getDir() == srcDir2
        assertSetContainsForAllTypes(tree2, 'subdir2/file2.txt')
    }

    void canUseFilterPatternsToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        touch(new File(srcDir1, 'subdir/ignored.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        touch(new File(srcDir2, 'subdir2/file1.txt'))
        touch(new File(srcDir2, 'subdir2/file2.txt'))
        touch(new File(srcDir2, 'subdir2/ignored.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'
        set.filter.include '**/file*'
        set.filter.exclude '**/file2*'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt', 'subdir2/file1.txt')
    }

    void ignoresSourceDirectoriesWhichDoNotExist() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))

        when:
        set.srcDir 'dir1'
        set.srcDir 'dir2'

        then:
        assertSetContainsForAllTypes(set, 'subdir/file1.txt')
    }

    void failsWhenSourceDirectoryIsNotADirectory() {
        File srcDir = new File(testDir, 'dir1')
        touch(srcDir)

        when:
        set.srcDir 'dir1'
        set.addToAntBuilder("node", "fileset")

        then:
        InvalidUserDataException e = thrown()
        e.message == "Source directory '$srcDir' is not a directory."
    }

    void hasNoDependenciesWhenNoSourceDirectoriesSpecified() {
        expect:
        dependencies(set).empty
    }

    void viewsHaveNoDependenciesWhenNoSourceDirectoriesSpecified() {
        expect:
        dependencies(set.sourceDirectories).empty
        dependencies(set.asFileTree).empty
        dependencies(set.matching {}).empty
    }

    void setAndItsViewsHaveDependenciesOfAllSourceDirectories() {
        given:
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        set.srcDir dir("dir1", task1)
        set.srcDir dir("dir2", task2)

        expect:
        dependencies(set) == [task1, task2] as Set
        dependencies(set.sourceDirectories) == [task1, task2] as Set
        dependencies(set.asFileTree) == [task1, task2] as Set
        dependencies(set.matching {}) == [task1, task2] as Set
    }

    void setAndItsViewsHaveDependenciesOfAllSourceDirectorySets() {
        given:
        def nested1 = new DefaultSourceDirectorySet('nested-1', '<nested-1>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
        def nested2 = new DefaultSourceDirectorySet('nested-2', '<nested-2>', patternSetFactory, taskDependencyFactory, fileCollectionFactory, directoryFileTreeFactory, objectFactory)
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        nested1.srcDir dir("dir1", task1)
        nested2.srcDir dir("dir2", task2)

        set.source nested1
        set.srcDir nested2.sourceDirectories

        expect:
        dependencies(set) == [task1, task2] as Set
        dependencies(set.sourceDirectories) == [task1, task2] as Set
        dependencies(set.asFileTree) == [task1, task2] as Set
        dependencies(set.matching {}) == [task1, task2] as Set
    }

    void canUseMatchingMethodToFilterCertainFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        touch(new File(srcDir1, 'subdir2/file1.txt'))

        when:
        set.srcDir 'dir1'
        FileTree filteredSet = set.matching {
            include '**/file1.txt'
            exclude 'subdir2/**'
        }

        then:
        assertSetContainsForAllTypes(filteredSet, 'subdir/file1.txt')
    }

    void canUsePatternsAndFilterPatternsAndMatchingMethodToFilterSourceFiles() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file1.other'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        touch(new File(srcDir1, 'subdir/ignored.txt'))
        touch(new File(srcDir1, 'subdir2/file1.txt'))

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

    void filteredSetIsLive() {
        File srcDir1 = new File(testDir, 'dir1')
        touch(new File(srcDir1, 'subdir/file1.txt'))
        touch(new File(srcDir1, 'subdir/file2.txt'))
        File srcDir2 = new File(testDir, 'dir2')
        touch(new File(srcDir2, 'subdir2/file1.txt'))

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

    void "compileBy can be called multiple times and only applies the last mapping"() {
        given:
        TaskProvider taskProvider1 = Mock()
        TaskProvider taskProvider2 = Mock()
        Function mapping1 = Mock()
        Function mapping2 = Mock()
        Action taskAction1
        Action taskAction2
        taskProvider1.configure(_) >> { Action action -> taskAction1 = action }
        taskProvider2.configure(_) >> { Action action -> taskAction2 = action }
        taskProvider1.flatMap(_) >> Mock(ProviderInternal)
        taskProvider2.flatMap(_) >> Mock(ProviderInternal)

        when:
        set.compiledBy(taskProvider1, mapping1)
        set.compiledBy(taskProvider2, mapping2)

        taskAction1.execute(null)
        taskAction2.execute(null)

        then:
        0 * mapping1.apply(_)
        1 * mapping2.apply(_) >> Mock(DirectoryProperty)
    }

    Set<Task> dependencies(Buildable buildable) {
        return buildable.buildDependencies.getDependencies(null)
    }

    FileCollection dir(String dirPath, Task builtBy) {
        def collection = fileCollectionFactory.configurableFiles()
        collection.from(dirPath)
        collection.builtBy(builtBy)
        return collection
    }
}
