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

package org.gradle.internal.snapshot.impl

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultSingletonFileTree
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.GeneratedSingletonFileTree
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ReadableResource
import org.gradle.api.resources.ResourceException
import org.gradle.api.resources.internal.LocalResourceAdapter
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable

class DefaultFileSystemSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileHasher = new TestFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), fileSystemMirror)

    def "fetches details of a file and caches the result"() {
        def f = tmpDir.createFile("f")
        expect:
        def snapshot = snapshotter.snapshot(f)
        snapshot.absolutePath == f.path
        snapshot.name == "f"
        snapshot.type == FileType.RegularFile
        snapshot.isContentAndMetadataUpToDate(new RegularFileSnapshot(f.path, f.absolutePath, fileHasher.hash(f), TestFiles.fileSystem().stat(f).lastModified))

        def snapshot2 = snapshotter.snapshot(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshot(d)
        snapshot.absolutePath == d.path
        snapshot.name == "d"
        snapshot.type == FileType.Directory

        def snapshot2 = snapshotter.snapshot(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a missing file and caches the result"() {
        def f = tmpDir.file("f")

        expect:
        def snapshot = snapshotter.snapshot(f)
        snapshot.absolutePath == f.path
        snapshot.name == "f"
        snapshot.type == FileType.Missing

        def snapshot2 = snapshotter.snapshot(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory hierarchy and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")

        expect:
        def snapshot = snapshotter.snapshot(d)
        getSnapshotInfo(snapshot) == [d.path, 5]

        def snapshot2 = snapshotter.snapshot(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of an empty directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshot(d)
        getSnapshotInfo(snapshot) == [d.absolutePath, 1]

        def snapshot2 = snapshotter.snapshot(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory tree with no patterns and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")
        def tree = dirTree(d)

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(tree.getDir(), tree.getPatterns())
        getSnapshotInfo(snapshot) == [d.path, 5]

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree.getDir(), tree.getPatterns())
        snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(tree.getDir(), tree.getPatterns())
        snapshot3.is(snapshot)
    }

    def "fetches details of a directory tree with patterns patterns and does not cache the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createFile("d1/f1")
        d.createDir("d2")
        d.createFile("d2/f1")
        d.createFile("d2/f2")
        def patterns = TestFiles.patternSetFactory.create()
        patterns.include "**/*1"
        def emptyPatterns = new PatternSet()

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d, patterns)
        getSnapshotInfo(snapshot) == [d.path, 6]

        def snapshot2 = snapshotter.snapshotDirectoryTree(d, patterns)
        !snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d, emptyPatterns)
        !snapshot3.is(snapshot)
        getSnapshotInfo(snapshot3) == [d.path, 8]

        def snapshot4 = snapshotter.snapshotDirectoryTree(d, emptyPatterns)
        !snapshot4.is(snapshot)
        snapshot4.is(snapshot3)
    }

    def "reuses cached unfiltered trees when looking for details of a filtered tree"() {
        given: "An existing snapshot"
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createFile("d1/f1")
        snapshotter.snapshotDirectoryTree(d, new PatternSet())

        and: "A filtered tree over the same directory"
        def patterns = TestFiles.patternSetFactory.create()
        patterns.include "**/*1"

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(d, patterns)
        def relativePaths = [] as Set
        snapshot.accept(new FileSystemSnapshotVisitor() {
            private Deque<String> relativePath = new ArrayDeque<String>()
            private boolean seenRoot = false

            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                if (!seenRoot) {
                    seenRoot = true
                } else {
                    relativePath.addLast(directorySnapshot.name)
                    relativePaths.add(relativePath.join("/"))
                }
                return true
            }

            @Override
            void visit(FileSystemLocationSnapshot fileSnapshot) {
                relativePath.addLast(fileSnapshot.name)
                relativePaths.add(relativePath.join("/"))
                relativePath.removeLast()
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                if (relativePath.isEmpty()) {
                    seenRoot = false
                } else {
                    relativePath.removeLast()
                }
            }
        })

        then: "The filtered tree uses the cached state"
        relativePaths == ["d1", "d1/f1", "f1"] as Set
    }

    def "snapshots a non-existing directory"() {
        given:
        def d = tmpDir.file("dir")

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(d, new PatternSet())

        then:
        getSnapshotInfo(snapshot) == [null, 0]
    }

    def "snapshots file as directory tree"() {
        given:
        def d = tmpDir.createFile("fileAsTree")

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(d, new PatternSet())

        then:
        getSnapshotInfo(snapshot) == [null, 1]
        snapshot.accept(new FileSystemSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                throw new UnsupportedOperationException()
            }

            @Override
            void visit(FileSystemLocationSnapshot fileSnapshot) {
                assert fileSnapshot.absolutePath == d.getAbsolutePath()
                assert fileSnapshot.name == d.name
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                throw new UnsupportedOperationException()
            }
        })
    }

    def "snapshots a singletonFileTree as RegularFileSnapshot"() {
        given:
        def tempDir = tmpDir.createDir('tmpDir')
        def file = tempDir.file('testFile')
        file.text = "content"

        when:
        def tree = new FileTreeAdapter(new DefaultSingletonFileTree(file), TestFiles.patternSetFactory)

        then:
        assertSingleFileTree(tree)

        assertSingleFileTree(tree.matching(new Action<PatternFilterable>() {
            @Override
            void execute(PatternFilterable patternFilterable) {
                patternFilterable.include(file.name)
            }
        }))

        assertEmptyTree(tree.matching(new Action<PatternFilterable>() {
            @Override
            void execute(PatternFilterable patternFilterable) {
                patternFilterable.exclude(file.name)
            }
        }))

        when:
        def singleFileTree = TestFiles.fileOperations(tempDir).fileTree(file)

        then:
        assertSingleFileTree(singleFileTree)
        assertSingleFileTree(singleFileTree.matching { include(file.name) })
        assertEmptyTree(singleFileTree.matching { exclude(file.name) })

        when:
        def fromConfigurableFiles = TestFiles.fileOperations(tempDir).configurableFiles(file).asFileTree

        then:
        assertSingleFileTree(fromConfigurableFiles)
        assertSingleFileTree(fromConfigurableFiles.matching { include(file.name) })
        assertEmptyTree(fromConfigurableFiles.matching { exclude(file.name) })
    }

    def "snapshots archive trees as RegularFileSnapshot"() {
        given:
        def tempDir = tmpDir.createDir('tmpDir')
        def archiveBaseDir = tempDir.createDir('archiveBase')
        def file = archiveBaseDir.createFile('file.txt')
        file.text = "content"

        when:
        TestFile zip = tempDir.file('archive.zip')
        archiveBaseDir.zipTo(zip)
        def zipTree = TestFiles.fileOperations(tempDir, testFileProvider()).zipTree(zip)
        def snapshots = snapshotter.snapshot(zipTree)

        then:
        assertSingleFileSnapshot(snapshots)

        when:
        TestFile tar = tempDir.file('archive.tar')
        archiveBaseDir.tarTo(tar)
        def tarTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(tar)
        snapshots = snapshotter.snapshot(tarTree)

        then:
        assertSingleFileSnapshot(snapshots)

        when:
        def tarDir = tmpDir.createDir('tarDir')
        TestFile emptyTar = tempDir.file('emptyArchive.tar')
        tarDir.tarTo(emptyTar)
        def emptyTarTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(tar)
        snapshots = snapshotter.snapshot(emptyTarTree)

        then:
        assertSingleFileSnapshot(snapshots)

        when:
        def tgzDir = tmpDir.createDir('tgzDir')
        TestFile tgz = tempDir.file('emptyArchive.tgz')
        tgzDir.tgzTo(tgz)
        def localResource = new LocalResourceAdapter(TestFiles.fileRepository().localResource(tgz))
        def emptyTgzTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(localResource)
        snapshots = snapshotter.snapshot(emptyTgzTree)

        then:
        assertSingleFileSnapshot(snapshots)

        when:
        def readableResource = new ReadableResource() {
            @Override
            InputStream read() throws MissingResourceException, ResourceException {
                return tgz.newInputStream()
            }

            @Override
            String getDisplayName() {
                return tgz.getName()
            }

            @Override
            URI getURI() {
                return tgz.toURI()
            }

            @Override
            String getBaseName() {
                return tgz.getName()
            }
        }
        def recourceTarTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(readableResource)
        snapshots = snapshotter.snapshot(recourceTarTree)

        then:
        assert snapshots.size() == 1
        assert getSnapshotInfo(snapshots[0]) == [null, 0]
    }

    def "snapshots a generated singletonFileTree as RegularFileSnapshot"() {
        given:
        def file = tmpDir.createFile('testFile')
        def tempDir = tmpDir.createDir('tmpDir')
        Factory<File> factory = new Factory<File>() {
            @Override
            File create() {
                return tempDir
            }
        }

        def action = new Action<OutputStream>() {
            @Override
            void execute(OutputStream outputStream) {
                outputStream.write("content".getBytes())
            }
        }

        when:
        def tree = new FileTreeAdapter(new GeneratedSingletonFileTree(factory, file.name, action))

        then:
        assertSingleFileTree(tree)
        assertSingleFileTree(tree.matching { include file.name })
        assertEmptyTree(tree.matching { exclude file.name })
    }

    private static DirectoryFileTree dirTree(File dir) {
        TestFiles.directoryFileTreeFactory().create(dir)
    }

    private TemporaryFileProvider testFileProvider() {
        new TemporaryFileProvider() {
            @Override
            File newTemporaryFile(String... path) {
                return tmpDir.createFile(path)
            }

            @Override
            File createTemporaryFile(String prefix, @Nullable String suffix, @Nullable String... path) {
                return null
            }

            @Override
            File createTemporaryDirectory(@Nullable String prefix, @Nullable String suffix, @Nullable String... path) {
                return tmpDir.createDir(path)
            }
        }
    }

    void assertSingleFileSnapshot(snapshots) {
        assert snapshots.size() == 1
        assert getSnapshotInfo(snapshots[0]) == [null, 1]
    }

    void assertEmptyTree(FileCollection fileCollection) {
        def snapshots = snapshotter.snapshot((FileCollectionInternal) fileCollection)
        assert snapshots.size() == 1
        assert snapshots[0] == FileSystemSnapshot.EMPTY
        assert fileCollection.files.empty
    }

    void assertSingleFileTree(FileCollection fileCollection) {
        assert fileCollection.files.size() == 1
        def file = fileCollection.files[0]
        def snapshots = snapshotter.snapshot((FileCollectionInternal) fileCollection)
        assertSingleFileSnapshot(snapshots)
        assert snapshots[0].absolutePath == file.absolutePath
    }

    private static List getSnapshotInfo(FileSystemSnapshot tree) {
        String rootPath = null
        int count = 0
        tree.accept(new FileSystemSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                if (rootPath == null) {
                    rootPath = directorySnapshot.absolutePath
                }
                count++
                return true
            }

            @Override
            void visit(FileSystemLocationSnapshot fileSnapshot) {
                count++
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
            }
        })
        return [rootPath, count]
    }
}
