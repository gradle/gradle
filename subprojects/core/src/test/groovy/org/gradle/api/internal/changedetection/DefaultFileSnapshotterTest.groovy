/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.changedetection

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ChangeListener
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.notNullValue
import static org.junit.Assert.assertThat

@RunWith(JMock.class)
public class DefaultFileSnapshotterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Hasher hasher = new DefaultHasher()
    private int counter
    private ChangeListener listener = context.mock(ChangeListener.class)
    private final DefaultFileSnapshotter snapshotter = new DefaultFileSnapshotter(hasher)
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Test
    public void getFilesReturnsOnlyTheFilesWhichExisted() {
        TestFile file = tmpDir.createFile('file1')
        TestFile dir = tmpDir.createDir('file2')
        TestFile noExist = tmpDir.file('file3')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file, dir, noExist))

        assertThat(snapshot.files.files as List, equalTo([file]))
    }
    
    @Test
    public void notifiesListenerWhenFileAdded() {
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1))

        context.checking {
            one(listener).added(file2)
        }
        snapshotter.snapshot(files(file1, file2)).changesSince(snapshot, listener)
    }

    @Test
    public void notifiesListenerWhenFileRemoved() {
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2))

        context.checking {
            one(listener).removed(file2)
        }
        snapshotter.snapshot(files(file1)).changesSince(snapshot, listener)
    }

    @Test
    public void fileHasNotChangedWhenTypeAndHashHaveNotChanged() {
        TestFile file = tmpDir.createFile('file')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))
        assertThat(snapshot, notNullValue())

        snapshotter.snapshot(files(file)).changesSince(snapshot, listener)
    }

    @Test
    public void fileHasChangedWhenTypeHasChanged() {
        TestFile file = tmpDir.createFile('file')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))

        file.delete()
        file.createDir()

        context.checking {
            one(listener).changed(file)
        }
        snapshotter.snapshot(files(file)).changesSince(snapshot, listener)
    }

    @Test
    public void fileHasChangedWhenHashHasChanged() {
        TestFile file = tmpDir.createFile('file')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))

        file.write('new content')

        context.checking {
            one(listener).changed(file)
        }
        snapshotter.snapshot(files(file)).changesSince(snapshot, listener)
    }

    @Test
    public void directoryHasNotChangedWhenTypeHasNotChanged() {
        TestFile dir = tmpDir.createDir('dir')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(dir))

        snapshotter.snapshot(files(dir)).changesSince(snapshot, listener)
    }

    @Test
    public void directoryHasChangedWhenTypeHasChanged() {
        TestFile dir = tmpDir.createDir('dir')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(dir))

        dir.deleteDir()
        dir.createFile()

        context.checking {
            one(listener).changed(dir)
        }
        snapshotter.snapshot(files(dir)).changesSince(snapshot, listener)
    }

    @Test
    public void nonExistentFileUnchangedWhenTypeHasNotChanged() {
        TestFile file = tmpDir.file('unknown')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))

        snapshotter.snapshot(files(file)).changesSince(snapshot, listener)
    }

    @Test
    public void nonExistentFileIsChangedWhenTypeHasChanged() {
        TestFile file = tmpDir.file('unknown')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))

        file.createFile()

        context.checking {
            one(listener).changed(file)
        }
        snapshotter.snapshot(files(file)).changesSince(snapshot, listener)
    }

    @Test
    public void ignoresDuplicatesInFileCollection() {
        TestFile file1 = tmpDir.createFile('file')
        TestFile file2 = tmpDir.createFile('file')

        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2))

        snapshotter.snapshot(files(file1)).changesSince(snapshot, listener)
    }

    @Test
    public void canCreateEmptySnapshot() {
        TestFile file = tmpDir.createFile('file')
        FileCollectionSnapshot snapshot = snapshotter.emptySnapshot()

        FileCollectionSnapshot newSnapshot = snapshotter.snapshot(files(file))

        context.checking {
            one(listener).added(file)
        }

        newSnapshot.changesSince(snapshot, listener)
    }

    @Test
    public void diffAddsAddedFilesToSnapshot() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.emptySnapshot()
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        context.checking {
            one(mergeListener).added(withParam(notNullValue()))
        }

        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        context.checking {
            one(listener).added(file)
        }
        target.changesSince(original, listener)
    }

    @Test
    public void canIgnoreAddedFileInDiff() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.emptySnapshot()
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        context.checking {
            one(mergeListener).added(withParam(notNullValue()))
            will {merge -> merge.ignore()}
        }

        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        target.changesSince(original, listener)
    }

    @Test
    public void diffAddsChangedFilesToSnapshot() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        file.write('new content')
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        context.checking {
            one(mergeListener).changed(withParam(notNullValue()))
        }

        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        context.checking {
            one(listener).changed(file)
        }
        target.changesSince(original, listener)
    }

    @Test
    public void canIgnoreChangedFileInDiff() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot target = snapshotter.snapshot(files(file))
        file.write('new content')
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        context.checking {
            one(mergeListener).changed(withParam(notNullValue()))
            will {merge -> merge.ignore()}
        }

        target = modified.changesSince(original).applyTo(target, mergeListener)

        target.changesSince(original, listener)
    }

    @Test
    public void diffRemovesDeletedFilesFromSnapshot() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.emptySnapshot()

        context.checking {
            one(mergeListener).removed(withParam(notNullValue()))
        }

        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.snapshot(files(file)), mergeListener)

        context.checking {
            one(listener).removed(file)
        }
        target.changesSince(original, listener)
    }

    @Test
    public void canIgnoreRemovedFileInDiff() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.emptySnapshot()

        context.checking {
            one(mergeListener).removed(withParam(notNullValue()))
            will{merge -> merge.ignore()}
        }

        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.snapshot(files(file)), mergeListener)

        target.changesSince(original, listener)
    }

    @Test
    public void diffIgnoresUnchangedFilesInSnapshot() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = context.mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))
        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        target.changesSince(snapshotter.emptySnapshot(), listener)
    }

    private FileCollection files(File... files) {
        FileTree collection = context.mock(FileTree.class)
        context.checking {
            allowing(collection).getAsFileTree()
            will(returnValue(collection))
            allowing(collection).iterator()
            will(returnIterator(files as List))
        }
        return collection
    }
    
}
