/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.DefaultFileVisitDetails
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.api.internal.hash.FileHasher
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED

@CleanupTestDirectory(fieldName = "tmpDir")
@Subject(DefaultClasspathSnapshotter)
@UsesNativeServices
class DefaultClasspathSnapshotterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def hasher = Stub(FileHasher)
    def stringInterner = Stub(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def fileSystem = TestFiles.fileSystem()
    def directoryFileTreeFactory = Mock(DirectoryFileTreeFactory)
    def fileSystemSnapshotter = new DefaultFileSystemSnapshotter(new DefaultFileHasher(), stringInterner, fileSystem, directoryFileTreeFactory, new DefaultFileSystemMirror([]))
    def classpathHasher = new DefaultClasspathEntryHasher(new DefaultClasspathContentHasher())
    def snapshotter = new DefaultClasspathSnapshotter(new FileSnapshotTreeFactory(fileSystemSnapshotter, directoryFileTreeFactory), stringInterner, classpathHasher)

    def "root elements are unsorted, non-root elements are sorted amongst themselves"() {
        given:
        def rootFile1 = tmpDir.file("root1.txt") << "root1"
        def rootDir = Spy(TestFile, constructorArgs: [tmpDir.file("dir").absolutePath]).createDir()
        def subFile1 = rootDir.file("file1.txt") << "file1"
        def subFile2 = rootDir.file("file2.txt") << "file2"
        def rootFile2 = tmpDir.file("root2.txt") << "root2"
        def rootDirTree = Mock(DirectoryFileTree)

        when:
        def snapshotInOriginalOrder = snapshotter.snapshot(files(rootFile1, rootDir, rootFile2), UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE)
        then:
        println snapshotInOriginalOrder.elements*.getName()
        snapshotInOriginalOrder.elements == [rootFile1, rootDir, subFile1, subFile2, rootFile2]
        1 * directoryFileTreeFactory.create(_ as File) >> rootDirTree
        _ * rootDirTree.patterns >> new PatternSet()
        _ * rootDirTree.getDir() >> rootFile1
        1 * rootDirTree.visit(_) >> { FileVisitor visitor ->
            visitor.visitFile(new DefaultFileVisitDetails(subFile1, fileSystem, fileSystem))
            visitor.visitFile(new DefaultFileVisitDetails(subFile2, fileSystem, fileSystem))
        }

        when:
        def snapshotInReverseOrder = snapshotter.snapshot(files(rootFile2, rootFile1, rootDir), UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE)
        then:
        snapshotInReverseOrder.elements == [rootFile2, rootFile1, rootDir, subFile1, subFile2]
        1 * directoryFileTreeFactory.create(rootDir) >> rootDirTree
        _ * rootDirTree.patterns >> new PatternSet()
        _ * rootDirTree.dir >> rootFile2
        1 * rootDirTree.visit(_) >> { FileVisitor visitor ->
            visitor.visitFile(new DefaultFileVisitDetails(subFile2, fileSystem, fileSystem))
            visitor.visitFile(new DefaultFileVisitDetails(subFile1, fileSystem, fileSystem))
        }
    }

    def files(File... files) {
        return new SimpleFileCollection(files)
    }
}
