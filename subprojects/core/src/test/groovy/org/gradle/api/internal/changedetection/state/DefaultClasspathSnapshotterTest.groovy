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
    def snapshots = [:]
    def snapshotter = new DefaultClasspathSnapshotter(
        new FileSnapshotTreeFactory(
            fileSystemSnapshotter, directoryFileTreeFactory
        ),
        stringInterner,
        jarCache
    ) {
        @Override
        protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
            return new RecordingFileCollectionSnapshotBuilder(super.createFileCollectionSnapshotBuilder(normalizationStrategy, compareStrategy), snapshots)
        }
    }

    def "root elements are unsorted, non-root elements are sorted amongst themselves"() {
        given:
        def rootFile1 = tmpDir.file("root1.txt") << "root1"
        def rootDir = tmpDir.file("dir").createDir()
        rootDir.file("file1.txt") << "file1"
        rootDir.file("file2.txt") << "file2"
        def rootFile2 = tmpDir.file("root2.txt") << "root2"

        when:
        def (hash, snapshots, fileCollectionSnapshots) = snapshot(rootFile1, rootDir, rootFile2)
        then:
        hash == 'bef588307f005b33f9c3192691d687b2'
        def expectedEntrySnapshots = [
            'dir': [
                'file1.txt': '60c913683cc577eae172594b76316d06',
                'file2.txt': 'e0d9760b191a5dc21838e8a16f956bb0',
                hash: '9d47e46923d97f1938e7689be6cef03a'
            ],
            'root2.txt': [
                hash: 'd41d8cd98f00b204e9800998ecf8427e'
            ],
            'root1.txt': [
                hash: 'd41d8cd98f00b204e9800998ecf8427e'
            ],
        ]
        snapshots == expectedEntrySnapshots
        fileCollectionSnapshots == [
            ['root1.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['dir', '', 'DIR'],
            ['file1.txt', 'file1.txt', '60c913683cc577eae172594b76316d06'],
            ['file2.txt', 'file2.txt', 'e0d9760b191a5dc21838e8a16f956bb0'],
            ['root2.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
        ]

        when:
        jarCache.allEntries.clear()
        (hash, snapshots, fileCollectionSnapshots) = snapshot(rootFile2, rootFile1, rootDir)
        then:
        hash == '4a41fdac8023c0182de12162ba283662'
        snapshots == expectedEntrySnapshots
        fileCollectionSnapshots == [
            ['root2.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['root1.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['dir', '', 'DIR'],
            ['file1.txt', 'file1.txt', '60c913683cc577eae172594b76316d06'],
            ['file2.txt', 'file2.txt', 'e0d9760b191a5dc21838e8a16f956bb0'],
        ]
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
        def (hash, snapshots, fileCollectionSnapshots) = snapshot(zipFile, classes)
        then:
        hash == '97092e2214481cf08d4b741cb549ae4d'

        fileCollectionSnapshots == [
            ['library.jar', '', 'f31495fd1bb4b8c3b8fb1f46a68adf9e'],
            ['classes', '', 'DIR'],
            ['fourthFile.txt', 'fourthFile.txt', '8fd6978401143ae9adc277e9ce819f7e'],
            ['build.log', 'subdir/build.log', 'abf951c0fe2b682313add34f016bcb30'],
            ['thirdFile.txt', 'thirdFile.txt', '728271a3405e112740bfd3198cfa70de']
        ]
        snapshots == [
            'library.jar': [
                'firstFile.txt': '9db5682a4d778ca2cb79580bdb67083f',
                'secondFile.txt': '82e72efeddfca85ddb625e88af3fe973',
                'subdir/someOtherFile.log': 'a9cca315f4b8650dccfa3d93284998ef',
                hash: 'f31495fd1bb4b8c3b8fb1f46a68adf9e'
            ],
            classes: [
                'fourthFile.txt': '8fd6978401143ae9adc277e9ce819f7e',
                'subdir/build.log': 'abf951c0fe2b682313add34f016bcb30',
                'thirdFile.txt': '728271a3405e112740bfd3198cfa70de',
                hash: 'fa5654d3c632f8b6e29ecaee439a5f15',
            ],
        ]
        jarCache.allEntries.size() == 1
        def key = jarCache.allEntries.keySet().iterator().next()
        jarCache.get(key).toString() == 'f31495fd1bb4b8c3b8fb1f46a68adf9e'
    }

    def files(File... files) {
        return new SimpleFileCollection(files)
    }

    def snapshot(File... classpath) {
        snapshots.clear()
        def fileCollectionSnapshot = snapshotter.snapshot(files(classpath), null, null)
        return [
            fileCollectionSnapshot.hash.toString(),
            snapshots,
            fileCollectionSnapshot.snapshots.collect { String path, NormalizedFileSnapshot normalizedFileSnapshot ->
                [new File(path).getName(), normalizedFileSnapshot.normalizedPath.path.toString(), normalizedFileSnapshot.snapshot.toString()]
            }
        ]
    }

    TestFile file(Object... path) {
        return new TestFile(tmpDir.getTestDirectory(), path)
    }

    trait RecordingCollector implements SnapshotCollector {
        abstract Map getSnapshots()
        abstract SnapshotCollector getDelegate()
        abstract String getPath()

        @Override
        void recordSnapshot(SnapshottableResource resource, HashCode hash) {
            report("Snapshot taken", resource.getRelativePath().toString(), hash)
            snapshots[resource.getRelativePath().toString()] = hash.toString()
            delegate.recordSnapshot(resource, hash)
        }

        @Override
        SnapshotCollector recordSubCollector(SnapshottableResource resource, SnapshotCollector collector) {
            def subSnapshots = [:]
            def subCollector = delegate.recordSubCollector(resource, new TotalReportingSnapshotCollector(subSnapshots, collector))
            snapshots[resource.getRelativePath().toString()] = subSnapshots
            return new RecordingSnapshotCollector(getFullPath(resource.getRelativePath().toString()), subCollector, subSnapshots)
        }

        private String getFullPath(String filePath) {
            return path ? "$path!$filePath" : filePath
        }

        @Override
        HashCode getHash(NormalizedFileSnapshotCollector collector) {
            def hash = delegate.getHash(collector)
            snapshots['hash'] = hash.toString()
            return hash
        }

        private void report(String type, String filePath, HashCode hash) {
            def event = "$type: ${getFullPath(filePath)} - $hash"
            println event
        }
    }

    static class RecordingFileCollectionSnapshotBuilder extends FileCollectionSnapshotBuilder implements RecordingCollector {
        final Map snapshots
        final FileCollectionSnapshotBuilder delegate

        RecordingFileCollectionSnapshotBuilder(FileCollectionSnapshotBuilder delegate, snapshots) {
            super(null, null, null)
            this.snapshots = snapshots
            this.delegate = delegate
        }

        @Override
        void collectSnapshot(NormalizedFileSnapshot normalizedSnapshot) {
            delegate.collectSnapshot(normalizedSnapshot)
        }

        @Override
        FileCollectionSnapshot build() {
            return delegate.build()
        }

        @Override
        String getPath() {
            return null
        }
    }

    static class RecordingSnapshotCollector implements RecordingCollector {
        String path
        final SnapshotCollector delegate
        final Map snapshots

        RecordingSnapshotCollector(String path, SnapshotCollector delegate, Map snapshots) {
            this.snapshots = snapshots
            this.path = path
            this.delegate = delegate
        }
    }

    static class TotalReportingSnapshotCollector implements SnapshotCollector {
        @Delegate(excludes = "getHash")
        private final SnapshotCollector delegate
        private final Map snapshots

        TotalReportingSnapshotCollector(Map snapshots, SnapshotCollector delegate) {
            this.snapshots = snapshots
            this.delegate = delegate
        }

        @Override
        HashCode getHash(NormalizedFileSnapshotCollector collector) {
            def hash = delegate.getHash(collector)
            snapshots['hash'] = hash.toString()
            return hash
        }
    }
}
