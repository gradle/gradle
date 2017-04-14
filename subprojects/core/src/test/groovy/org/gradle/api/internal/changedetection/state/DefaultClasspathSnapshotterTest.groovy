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

import com.google.common.hash.HashCode
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryIndexedCache
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

    def stringInterner = Stub(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def fileSystem = TestFiles.fileSystem()
    def directoryFileTreeFactory = TestFiles.directoryFileTreeFactory()
    def fileSystemSnapshotter = new DefaultFileSystemSnapshotter(new DefaultFileHasher(), stringInterner, fileSystem, directoryFileTreeFactory, new DefaultFileSystemMirror([]))
    PersistentIndexedCache<HashCode, HashCode> jarCache = new InMemoryIndexedCache<>(new HashCodeSerializer())
    def snapshotter = new DefaultClasspathSnapshotter(
        new FileSnapshotTreeFactory(
            fileSystemSnapshotter, directoryFileTreeFactory
        ),
        stringInterner,
        jarCache
    )

    def "root elements are unsorted, non-root elements are sorted amongst themselves"() {
        given:
        def rootFile1 = tmpDir.file("root1.txt") << "root1"
        def rootDir = tmpDir.file("dir").createDir()
        def subFile1 = rootDir.file("file1.txt") << "file1"
        def subFile2 = rootDir.file("file2.txt") << "file2"
        def rootFile2 = tmpDir.file("root2.txt") << "root2"

        when:
        def snapshotInOriginalOrder = snapshotter.snapshot(files(rootFile1, rootDir, rootFile2), UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE)
        then:
        println snapshotInOriginalOrder.elements*.getName()
        snapshotInOriginalOrder.elements == [rootFile1, rootDir, subFile1, subFile2, rootFile2]

        when:
        def snapshotInReverseOrder = snapshotter.snapshot(files(rootFile2, rootFile1, rootDir), UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE)
        then:
        snapshotInReverseOrder.elements == [rootFile2, rootFile1, rootDir, subFile1, subFile2]
    }

    def files(File... files) {
        return new SimpleFileCollection(files)
    }
}
