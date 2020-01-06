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
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultSingletonFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.GeneratedSingletonFileTree
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ReadableResource
import org.gradle.api.resources.ResourceException
import org.gradle.api.resources.internal.LocalResourceAdapter
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.internal.Factory
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable
import java.util.function.Consumer

class DefaultFileCollectionSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def snapshotter = TestFiles.fileCollectionSnapshotter()
    def noopAction = {} as Consumer<String>


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
        def resourceTarTree = TestFiles.fileOperations(tempDir, testFileProvider()).tarTree(readableResource)
        snapshots = snapshotter.snapshot(resourceTarTree)

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
        def tree = new FileTreeAdapter(new GeneratedSingletonFileTree(factory, file.name, noopAction, action))

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
            File createTemporaryFile(String prefix, @Nullable String suffix, @Nullable String... path) {
                return null
            }

            @Override
            File createTemporaryDirectory(@Nullable String prefix, @Nullable String suffix, @Nullable String... path) {
                return tmpDir.createDir(path)
            }
        }
    }

    void assertEmptyTree(FileCollection fileCollection) {
        def snapshots = snapshotter.snapshot((FileCollectionInternal) fileCollection)
        assert snapshots.size() == 0
        assert fileCollection.files.empty
    }

    void assertSingleFileTree(FileCollection fileCollection) {
        assert fileCollection.files.size() == 1
        def file = fileCollection.files[0]
        def snapshots = snapshotter.snapshot((FileCollectionInternal) fileCollection)
        assertSingleFileSnapshot(snapshots)
        assert snapshots[0].absolutePath == file.absolutePath
    }


    void assertSingleFileSnapshot(snapshots) {
        assert snapshots.size() == 1
        assert getSnapshotInfo(snapshots[0]) == [null, 1]
    }

    private static List getSnapshotInfo(FileSystemSnapshot tree) {
        String rootPath = null
        int count = 0
        tree.accept(new FileSystemSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                if (rootPath == null) {
                    rootPath = directorySnapshot.absolutePath
                }
                count++
                return true
            }

            @Override
            void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                count++
            }

            @Override
            void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            }
        })
        return [rootPath, count]
    }
}
