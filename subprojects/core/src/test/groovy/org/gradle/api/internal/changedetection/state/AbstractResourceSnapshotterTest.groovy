/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter
import org.gradle.api.internal.changedetection.resources.Snapshottable
import org.gradle.api.internal.changedetection.resources.SnapshottableResource
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.caching.internal.BuildCacheHasher
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory(fieldName = "tmpDir")
@UsesNativeServices
class AbstractResourceSnapshotterTest extends Specification {
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
    ResourceSnapshotter snapshotter

    def files(File... files) {
        return new SimpleFileCollection(files)
    }

    def snapshot(File... classpath) {
        snapshots.clear()
        def fileCollectionSnapshot = fileSystemSnapshotter.snapshotFileCollection(files(classpath),
            new ReportingSnapshotter(snapshotter, snapshots))
        return [
            fileCollectionSnapshot.hash.toString(),
            snapshots,
            fileCollectionSnapshot.snapshots.collect { String path, NormalizedFileSnapshot normalizedFileSnapshot ->
                [new File(path).getName(), normalizedFileSnapshot.normalizedPath.path, normalizedFileSnapshot.snapshot.toString()]
            }
        ]
    }

    TestFile file(Object... path) {
        return new TestFile(tmpDir.getTestDirectory(), path)
    }

}

class ReportingSnapshotter implements ResourceSnapshotter {
    private final ResourceSnapshotter delegate
    private final Map snapshots

    ReportingSnapshotter(ResourceSnapshotter delegate, Map snapshots) {
        this.snapshots = snapshots
        this.delegate = delegate
    }

    @Override
    void snapshot(Snapshottable snapshottable, SnapshottingResultRecorder recorder) {
        delegate.snapshot(snapshottable, recorder)
    }

    @Override
    SnapshottingResultRecorder createResultRecorder() {
        return new ReportingSnapshottingResultRecorder(null, delegate.createResultRecorder(), snapshots)
    }

    @Override
    void appendConfigurationToHasher(BuildCacheHasher hasher) {
        delegate.appendConfigurationToHasher(hasher)
    }
}

class ReportingSnapshottingResultRecorder implements SnapshottingResultRecorder {
    String path
    final SnapshottingResultRecorder delegate
    final Map snapshots

    ReportingSnapshottingResultRecorder(String path, SnapshottingResultRecorder delegate, Map snapshots) {
        this.snapshots = snapshots
        this.path = path
        this.delegate = delegate
    }

    @Override
    void recordResult(SnapshottableResource resource, HashCode hash) {
        report("Snapshot taken", resource.getRelativePath().toString(), hash)
        snapshots[resource.getRelativePath().toString()] = hash.toString()
        delegate.recordResult(resource, hash)
    }

    @Override
    SnapshottingResultRecorder recordCompositeResult(SnapshottableResource resource, SnapshottingResultRecorder recorder) {
        def subSnapshots = [:]
        def compositeRecorder = delegate.recordCompositeResult(resource, new TotalReportingResultRecorder(subSnapshots, recorder))
        snapshots[resource.getRelativePath().toString()] = subSnapshots
        return new ReportingSnapshottingResultRecorder(getFullPath(resource.getRelativePath().toString()), compositeRecorder, subSnapshots)
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

    @Override
    TaskFilePropertyCompareStrategy getCompareStrategy() {
        return delegate.getCompareStrategy()
    }

    @Override
    boolean isNormalizedPathAbsolute() {
        return delegate.isNormalizedPathAbsolute()
    }
}

class TotalReportingResultRecorder implements SnapshottingResultRecorder {
    @Delegate(excludes = "getHash")
    private final SnapshottingResultRecorder delegate
    private final Map snapshots

    TotalReportingResultRecorder(Map snapshots, SnapshottingResultRecorder delegate) {
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
