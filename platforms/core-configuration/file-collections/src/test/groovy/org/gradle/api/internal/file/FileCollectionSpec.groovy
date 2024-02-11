/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContains
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForFileSet
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForMatchingTask
import static org.gradle.util.internal.WrapUtil.toSet

@UsesNativeServices // via DirectoryFileTree
abstract class FileCollectionSpec extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())

    abstract AbstractFileCollection containing(File... files)

    def isEmptyWhenContainsNoFiles() {
        expect:
        containing().isEmpty()
        !containing(new File("f1")).isEmpty()
    }

    def usesDisplayNameAsToString() {
        def collection = containing(new File("f1"))

        expect:
        collection.toString() == collection.displayName
    }

    def canQueryFilesAndRetainOrder() {
        def file1 = new File("f1")
        def file2 = new File("f2")
        def collection = containing(file1, file2, file1, file2)

        expect:
        collection.files as List == [file1, file2]
    }

    def canIterateOverFilesRetainingOrder() {
        def file1 = new File("f1")
        def file2 = new File("f2")
        def collection = containing(file1, file2, file1, file2)
        def iterator = collection.iterator()

        expect:
        iterator.next() == file1
        iterator.next() == file2
        !iterator.hasNext()
    }

    def canViewElementsOfEmptyCollectionAsProvider() {
        def collection = containing()
        def elements = collection.elements

        expect:
        elements.present
        elements.get().empty
    }

    def canViewElementsAsProviderRetainingOrder() {
        def file1 = new File("f1")
        def file2 = new File("f2")
        def collection = containing(file1, file2, file1, file2)
        def elements = collection.elements

        expect:
        elements.present
        elements.get()*.asFile == [file1, file2]
    }

    void canAddToAntBuilderAsResourceCollection() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        def collection = containing(file1, file2)

        expect:
        assertSetContains(collection, toSet("f1", "f2"))
    }

    void includesOnlyExistingFilesWhenAddedToAntBuilderAsAFileSetOrMatchingTask() {
        TestFile testDir = this.testDir.getTestDirectory()
        TestFile file1 = testDir.file("f1").createFile()
        TestFile dir1 = testDir.file("dir1").createDir()
        TestFile file2 = dir1.file("f2").createFile()
        TestFile missing = testDir.file("f3")
        testDir.file("f2").createFile()
        testDir.file("ignored1").createFile()
        dir1.file("f1").createFile()
        dir1.file("ignored1").createFile()
        def collection = containing(file1, file2, dir1, missing)

        expect:
        assertSetContainsForFileSet(collection, toSet("f1", "f2"))
        assertSetContainsForMatchingTask(collection, toSet("f1", "f2"))
    }

    void includesFilesAndContentsOfDirectoriesWhenConvertedToTreeAndAddedToAntBuilder() {
        TestFile testDir = this.testDir.getTestDirectory()
        TestFile file1 = testDir.file("f1").createFile()
        TestFile dir1 = testDir.file("dir1").createDir()
        dir1.file("f2").createFile()
        dir1.file("subdir/f2").createFile()
        TestFile missing = testDir.file("f3")
        testDir.file("f2").createFile()
        testDir.file("ignored1").createFile()
        def collection = containing(file1, dir1, missing)
        def tree = collection.asFileTree

        expect:
        assertSetContainsForFileSet(tree, toSet("f1", "f2", "subdir/f2"))
        assertSetContainsForMatchingTask(tree, toSet("f1", "f2", "subdir/f2"))
    }
}
