/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultFileSystemMirror
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.DefaultWellKnownFileLocations
import org.gradle.api.internal.changedetection.state.FileContentSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.hash.TestFileHasher
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile

class PathNormalizationStrategyTest extends AbstractProjectBuilderSpec {
    private StringInterner stringInterner = new StringInterner()

    public static final String IGNORED = "IGNORED"
    List<PhysicalSnapshot> roots
    TestFile jarFile1
    TestFile jarFile2
    TestFile resources
    String subDirA = "a"
    String subDirB = "b"
    String fileInRoot = "input.txt"
    String fileInSubdirA = "a/input-1.txt"
    String fileInSubdirB = "b/input-2.txt"
    TestFile emptyDir
    TestFile missingFile

    def setup() {
        StringInterner interner = Mock(StringInterner) {
            intern(_) >> { String string -> string }
        }

        jarFile1 = file("dir/libs/library-a.jar")
        jarFile1 << "JAR file #1"
        jarFile2 = file("dir/libs/library-b.jar")
        jarFile2 << "JAR file #2"
        resources = file("dir/resources")
        resources.file("input.txt") << "main input"
        resources.file("a/input-1.txt") << "input #1"
        resources.file("b/input-2.txt") << "input #2"
        emptyDir = file("empty-dir")
        emptyDir.mkdirs()
        missingFile = file("missing-file")

        def directoryFileTreeFactory = TestFiles.directoryFileTreeFactory()
        def snapshotter = new DefaultFileSystemSnapshotter(new TestFileHasher(), interner, TestFiles.fileSystem(), directoryFileTreeFactory, new DefaultFileSystemMirror(new DefaultWellKnownFileLocations([])))

        roots = [
            snapshotter.snapshotSelf(jarFile1),
            snapshotter.snapshotSelf(this.jarFile2),
            snapshotter.snapshotDirectoryTree(directoryFileTreeFactory.create(this.resources)),
            snapshotter.snapshotDirectoryTree(directoryFileTreeFactory.create(this.emptyDir)),
            snapshotter.snapshotSelf(missingFile)
        ]
    }


    def "sensitivity NONE"() {
        def snapshots = collectSnapshots(new IgnoredPathFingerprintingStrategy())
        expect:
        allFilesToSnapshot.each { file ->
            if (file.isFile() || !file.exists()) {
                assert snapshots[file] == IGNORED
            } else {
                assert snapshots[file] == null
            }
        }
    }

    def "sensitivity NAME_ONLY"() {
        def snapshots = collectSnapshots(new NameOnlyFingerprintingStrategy())
        expect:
        (allFilesToSnapshot - emptyDir - resources).each { file ->
            assert snapshots[file] == file.name
        }
        snapshots[emptyDir] == IGNORED
        snapshots[resources] == IGNORED
    }

    def "sensitivity RELATIVE"() {
        def snapshots = collectSnapshots(new RelativePathFingerprintingStrategy(stringInterner))
        expect:
        snapshots[jarFile1]                      == jarFile1.name
        snapshots[jarFile2]                      == jarFile2.name
        snapshots[resources]                     == IGNORED
        snapshots[resources.file(fileInRoot)]    == fileInRoot
        snapshots[resources.file(subDirA)]       == subDirA
        snapshots[resources.file(fileInSubdirA)] == fileInSubdirA
        snapshots[resources.file(subDirB)]       == subDirB
        snapshots[resources.file(fileInSubdirB)] == fileInSubdirB
        snapshots[emptyDir]                      == IGNORED
        snapshots[missingFile]                   == missingFile.name
    }

    def "sensitivity ABSOLUTE (include missing = true)"() {
        def snapshots = collectSnapshots(new AbsolutePathFingerprintingStrategy(true))
        expect:
        allFilesToSnapshot.each { file ->
            assert snapshots[file] == file.absolutePath
        }
        snapshots.size() == allFilesToSnapshot.size()
    }

    def "sensitivity ABSOLUTE (include missing = false)"() {
        def snapshots = collectSnapshots(new AbsolutePathFingerprintingStrategy(false))
        expect:
        (allFilesToSnapshot - missingFile).each { file ->
            assert snapshots[file] == file.absolutePath
        }
        snapshots.size() == allFilesToSnapshot.size() - 1
    }

    List<File> getAllFilesToSnapshot() {
        [jarFile1, jarFile2, resources, emptyDir, missingFile] + [fileInRoot, subDirA, fileInSubdirA, subDirB, fileInSubdirB].collect { resources.file(it) }
    }

    protected TestFile file(String path) {
        new TestFile(project.file(path))
    }

    protected def collectSnapshots(FingerprintingStrategy strategy) {
        strategy.collectSnapshots(roots)
        Map<File, String> snapshots = [:]
        strategy.collectSnapshots(roots).each { path, normalizedSnapshot ->
            String normalizedPath
            if (normalizedSnapshot instanceof FileContentSnapshot) {
                normalizedPath = IGNORED
            } else {
                normalizedPath = normalizedSnapshot.normalizedPath
            }
            snapshots.put(new File(path), normalizedPath)
        }
        return snapshots

    }
}
