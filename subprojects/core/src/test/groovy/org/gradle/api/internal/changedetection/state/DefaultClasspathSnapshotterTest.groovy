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
import org.gradle.api.internal.changedetection.resources.SnapshotCollector
import org.gradle.api.internal.changedetection.resources.SnapshottableResource
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
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
    InMemoryIndexedCache<HashCode, HashCode> jarCache = new InMemoryIndexedCache<>(new HashCodeSerializer())
    def events = []
    def snapshotter = new DefaultClasspathSnapshotter(
        new FileSnapshotTreeFactory(
            fileSystemSnapshotter, directoryFileTreeFactory
        ),
        stringInterner,
        jarCache
    ) {
        @Override
        protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
            return new RecordingFileCollectionSnapshotBuilder(events, super.createFileCollectionSnapshotBuilder(normalizationStrategy, compareStrategy))
        }
    }

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

    def "snapshots runtime classpath files"() {
        def zipFile = file('library.jar')
        file('zipContents').create {
            file('firstFile.txt').text = "Some text"
            file('secondFile.txt').text = "Second File"
            subdir {
                file('someOtherFile.log').text = "File in subdir"
            }
        }.zipTo(zipFile)
        def classes = file('classes').create {
            file('thirdFile.txt').text = "Third file"
            file('fourthFile.txt').text = "Fourth file"
            subdir {
                file('build.log').text = "File in subdir"
            }
        }

        when:
        def (hash, events, fileCollectionSnapshots) = snapshot(zipFile, classes)
        then:
        events == [
            'Snapshot taken: classes!fourthFile.txt - 8fd6978401143ae9adc277e9ce819f7e',
            'Snapshot taken: classes!subdir/build.log - abf951c0fe2b682313add34f016bcb30',
            'Snapshot taken: classes!thirdFile.txt - 728271a3405e112740bfd3198cfa70de',
            'Snapshot taken: library.jar!firstFile.txt - 9db5682a4d778ca2cb79580bdb67083f',
            'Snapshot taken: library.jar!secondFile.txt - 82e72efeddfca85ddb625e88af3fe973',
            'Snapshot taken: library.jar!subdir/someOtherFile.log - a9cca315f4b8650dccfa3d93284998ef',
        ]

        hash == '97092e2214481cf08d4b741cb549ae4d'

        fileCollectionSnapshots == [
            'library.jar':['', 'f31495fd1bb4b8c3b8fb1f46a68adf9e'],
            'classes':['', 'DIR'],
            'fourthFile.txt':['fourthFile.txt', '8fd6978401143ae9adc277e9ce819f7e'],
            'build.log':['subdir/build.log', 'abf951c0fe2b682313add34f016bcb30'],
            'thirdFile.txt':['thirdFile.txt', '728271a3405e112740bfd3198cfa70de']
        ]
        jarCache.allEntries.size() == 1
        def key = jarCache.allEntries.keySet().iterator().next()
        jarCache.get(key).toString() == 'f31495fd1bb4b8c3b8fb1f46a68adf9e'
    }

    def files(File... files) {
        return new SimpleFileCollection(files)
    }

    def snapshot(File... classpath) {
        events.clear()
        def fileCollectionSnapshot = snapshotter.snapshot(files(classpath), null, null)
        events.sort()
        return [
            fileCollectionSnapshot.hash.toString(),
            events,
            fileCollectionSnapshot.snapshots.collectEntries { String path, NormalizedFileSnapshot normalizedFileSnapshot ->
                [(new File(path).getName()): [normalizedFileSnapshot.normalizedPath.path, normalizedFileSnapshot.snapshot.toString()]]
            }
        ]
    }

    TestFile file(Object... path) {
        return new TestFile(tmpDir.root, path)
    }

    static class RecordingFileCollectionSnapshotBuilder extends FileCollectionSnapshotBuilder {
        private List<String> events
        private final FileCollectionSnapshotBuilder delegate

        RecordingFileCollectionSnapshotBuilder(List<String> events, FileCollectionSnapshotBuilder delegate) {
            super(null, null, null)
            this.events = events
            this.delegate = delegate
        }

        @Override
        void collectSnapshot(NormalizedFileSnapshot normalizedSnapshot) {
            report("Snapshot taken", normalizedSnapshot.normalizedPath.path, normalizedSnapshot.getSnapshot().getContentMd5())
            delegate.collectSnapshot(normalizedSnapshot)
        }

        @Override
        FileCollectionSnapshot build() {
            return delegate.build()
        }

        @Override
        void recordSnapshot(SnapshottableResource resource, HashCode hash) {
            report("Snapshot taken", resource.getRelativePath().toString(), hash)
            delegate.recordSnapshot(resource, hash)
        }

        @Override
        SnapshotCollector recordSubCollector(SnapshottableResource resource, SnapshotCollector collector) {
            def subCollector = delegate.recordSubCollector(resource, collector)
            return new RecordingSnapshotCollector(resource.getRelativePath().toString(), events, subCollector)
        }

        @Override
        HashCode getHash(NormalizedFileSnapshotCollector collector) {
            return delegate.getHash(collector)
        }

        private void report(String type, String filePath, HashCode hash) {
            def event = "$type: ${filePath} - $hash"
            events.add(event)
            println event
        }
    }
    static class RecordingSnapshotCollector implements SnapshotCollector {
        private String path
        private List<String> events
        private final SnapshotCollector delegate

        RecordingSnapshotCollector(String path, List<String> events, SnapshotCollector delegate) {
            this.path = path
            this.events = events
            this.delegate = delegate
        }

        @Override
        void recordSnapshot(SnapshottableResource resource, HashCode hash) {
            report("Snapshot taken", resource.getRelativePath().toString(), hash)
            delegate.recordSnapshot(resource, hash)
        }

        @Override
        SnapshotCollector recordSubCollector(SnapshottableResource resource, SnapshotCollector collector) {
            def subCollector = delegate.recordSubCollector(resource, collector)
            return new RecordingSnapshotCollector(getFullPath(resource.getRelativePath().toString()), events, subCollector)
        }

        @Override
        HashCode getHash(NormalizedFileSnapshotCollector collector) {
            return delegate.getHash(collector)
        }

        private String getFullPath(String filePath) {
            return path ? "$path!$filePath" : filePath
        }

        private void report(String type, String filePath, HashCode hash) {
            def event = "$type: ${getFullPath(filePath)} - $hash"
            events.add(event)
            println event
        }
    }
}
