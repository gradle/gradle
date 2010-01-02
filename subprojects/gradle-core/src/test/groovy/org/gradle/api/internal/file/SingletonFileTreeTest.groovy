/*
 * Copyright 2007, 2008 the original author or authors.
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

import org.gradle.api.file.FileTree
import org.gradle.api.tasks.TaskDependency
import org.gradle.util.TestFile
import org.gradle.util.TemporaryFolder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.gradle.api.file.FileVisitorUtil.*
import static org.gradle.api.tasks.AntBuilderAwareUtil.*
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.Task

class SingletonFileTreeTest {
    @Rule public TemporaryFolder rootDir = new TemporaryFolder();
    private final TestFile testFile = rootDir.dir.file('test.txt')
    private final TestFile missingFile = rootDir.dir.file('missing')
    private final TestFile testDir = rootDir.dir.file('test-dir')
    private final TaskDependency dependency = [:] as TaskDependency

    @Before
    public void setUp() {
        testFile.touch()
        testDir.create {
            subdir1 {
                file 'file1.txt'
                file 'file2.txt'
            }
            subdir2 {
                file 'file1.txt'
            }
        }
    }

    @Test
    public void hasUsefulDisplayName() {
        SingletonFileTree tree = new SingletonFileTree(testFile, dependency)
        assertThat(tree.displayName, equalTo("file '$testFile'".toString()));
    }

    @Test
    public void containsASingleFileWhenSourceIsAFile() {
        SingletonFileTree tree = new SingletonFileTree(testFile, dependency)
        assertThat(tree.files, equalTo([testFile] as Set))
    }

    @Test
    public void containsDescendentFilesWhenSourceIsADirectory() {
        SingletonFileTree tree = new SingletonFileTree(testDir, dependency)
        assertThat(tree.files, equalTo(testDir.files('subdir1/file1.txt', 'subdir1/file2.txt', 'subdir2/file1.txt') as Set))
    }

    @Test
    public void isEmptyWhenSourceDoesNotExist() {
        SingletonFileTree tree = new SingletonFileTree(missingFile, dependency)
        assertThat(tree.files, isEmpty())
    }

    @Test
    public void addsToAntBuilderWhenSourceIsAFile() {
        SingletonFileTree tree = new SingletonFileTree(testFile, dependency)
        assertSetContains(tree, 'test.txt')
    }

    @Test
    public void addsToAntBuilderWhenSourceIsADirectory() {
        SingletonFileTree tree = new SingletonFileTree(testDir, dependency)
        assertSetContains(tree, 'subdir1/file1.txt', 'subdir1/file2.txt', 'subdir2/file1.txt')
    }

    @Test
    public void addsToAntBuilderWhenSourceDoesNotExist() {
        SingletonFileTree tree = new SingletonFileTree(missingFile, dependency)
        assertSetContains(tree)
    }

    @Test
    public void visitsASingleFileWhenSourceIsAFile() {
        SingletonFileTree tree = new SingletonFileTree(testFile, dependency)
        assertVisits(tree, ['test.txt'], [])
    }

    @Test
    public void visitsDescendentFilesWhenSourceIsADirectory() {
        SingletonFileTree tree = new SingletonFileTree(testDir, dependency)
        assertVisits(tree, ['subdir1/file1.txt', 'subdir1/file2.txt', 'subdir2/file1.txt'], ['subdir1', 'subdir2'])
    }

    @Test
    public void visitsNothingWhenSourceDoesNotExist() {
        SingletonFileTree tree = new SingletonFileTree(missingFile, dependency)
        assertVisits(tree, [], [])
    }

    @Test
    public void canRestrictContentsWhenSourceIsAFile() {
        SingletonFileTree tree = new SingletonFileTree(testFile, dependency)

        FileTree filtered = tree.matching { exclude '*.txt' }
        assertThat(filtered.files, isEmpty())
        assertSetContains(filtered)
        assertVisits(filtered, [], [])

        filtered = tree.matching { include '*.txt' }
        assertThat(filtered.files, equalTo([testFile] as Set))
        assertSetContains(filtered, 'test.txt')
        assertVisits(filtered, ['test.txt'], [])
    }

    @Test
    public void canRestrictContentsWhenSourceIsADirectory() {
        SingletonFileTree tree = new SingletonFileTree(testDir, dependency)

        FileTree filtered = tree.matching { exclude '**/file1.txt' }
        assertThat(filtered.files, equalTo([testDir.file('subdir1/file2.txt')] as Set))
        assertSetContains(filtered, 'subdir1/file2.txt')
        assertVisits(filtered, ['subdir1/file2.txt'], ['subdir1', 'subdir2'])

        filtered = tree.matching { include '**/file2.txt' }
        assertThat(filtered.files, equalTo([testDir.file('subdir1/file2.txt')] as Set))
        assertSetContains(filtered, 'subdir1/file2.txt')
        assertVisits(filtered, ['subdir1/file2.txt'], ['subdir1', 'subdir2'])
    }

    @Test
    public void canRestrictContentsWhenSourceDoesNotExist() {
        SingletonFileTree tree = new SingletonFileTree(missingFile, dependency)

        FileTree filtered = tree.matching { exclude '*.txt' }
        assertThat(filtered.files, isEmpty())
        assertSetContains(filtered)
        assertVisits(filtered, [], [])
    }

    @Test
    public void hasSpecifiedDependencies() {
        Task task = [:] as Task
        SingletonFileTree tree = new SingletonFileTree(missingFile, [getDependencies: { [task] as Set}] as TaskDependency)
        assertThat(tree.buildDependencies.getDependencies(null), equalTo([task] as Set))
        assertThat(tree.matching{}.buildDependencies.getDependencies(null), equalTo([task] as Set))
    }
}


