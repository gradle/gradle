/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.fingerprint.impl

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.resources.internal.LocalResourceAdapter
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.internal.Factory
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable

class DefaultFileCollectionSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def snapshotter = TestFiles.fileCollectionSnapshotter()

    def "snapshots a tree with file as root as RegularFileSnapshot"() {
        given:
        def tempDir = tmpDir.createDir('tmpDir')
        def file = tempDir.file('testFile')
        file.text = "content"

        when:
        def tree = new FileTreeAdapter(TestFiles.directoryFileTreeFactory().create(file), TestFiles.taskDependencyFactory(), TestFiles.patternSetFactory)

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
        def snapshot = snapshotter.snapshot(zipTree).snapshot

        then:
        assertSingleFileSnapshot(snapshot)

        when:
        TestFile tar = tempDir.file('archive.tar')
        archiveBaseDir.tarTo(tar)
        def tarTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(tar)
        snapshot = snapshotter.snapshot(tarTree).snapshot

        then:
        assertSingleFileSnapshot(snapshot)

        when:
        def tarDir = tmpDir.createDir('tarDir')
        TestFile emptyTar = tempDir.file('emptyArchive.tar')
        tarDir.tarTo(emptyTar)
        def emptyTarTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(tar)
        snapshot = snapshotter.snapshot(emptyTarTree).snapshot

        then:
        assertSingleFileSnapshot(snapshot)

        when:
        def tgzDir = tmpDir.createDir('tgzDir')
        TestFile tgz = tempDir.file('emptyArchive.tgz')
        tgzDir.tgzTo(tgz)
        def localResource = new LocalResourceAdapter(TestFiles.fileRepository().localResource(tgz))
        def emptyTgzTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(localResource)
        snapshot = snapshotter.snapshot(emptyTgzTree).snapshot

        then:
        assertSingleFileSnapshot(snapshot)
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
        def tree = TestFiles.fileCollectionFactory().generated(factory, file.name, {}, action)

        then:
        assertSingleFileTree(tree)
        assertSingleFileTree(tree.matching { include file.name })
        assertEmptyTree(tree.matching { exclude file.name })
    }

    private TemporaryFileProvider testFileProvider() {
        new TemporaryFileProvider() {
            @Override
            File newTemporaryFile(String... path) {
                return tmpDir.createFile(path)
            }

            @Override
            File newTemporaryDirectory(String... path) {
                return tmpDir.createDir(path)
            }

            @Override
            Factory<File> temporaryDirectoryFactory(String... path) {
                return null
            }

            @Override
            File createTemporaryFile(String prefix, @Nullable String suffix, String... path) {
                return null
            }

            @Override
            File createTemporaryDirectory(@Nullable String prefix, @Nullable String suffix, String... path) {
                return tmpDir.createDir(path)
            }
        }
    }

    void assertEmptyTree(FileCollection fileCollection) {
        def snapshot = snapshotter.snapshot(fileCollection).snapshot
        assert snapshot == FileSystemSnapshot.EMPTY
        assert fileCollection.files.empty
    }

    void assertSingleFileTree(FileCollection fileCollection) {
        assert fileCollection.files.size() == 1
        def file = fileCollection.files[0]
        def snapshot = snapshotter.snapshot(fileCollection).snapshot
        assertSingleFileSnapshot(snapshot)
        assert snapshot.absolutePath == file.absolutePath
    }

    void assertSingleFileSnapshot(snapshot) {
        assert snapshot instanceof RegularFileSnapshot
    }
}
