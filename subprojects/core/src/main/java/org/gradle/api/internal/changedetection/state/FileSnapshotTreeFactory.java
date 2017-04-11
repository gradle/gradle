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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class FileSnapshotTreeFactory {
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public FileSnapshotTreeFactory(FileSystemSnapshotter fileSystemSnapshotter, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    public DefaultFileSnapshotTree fileTree(FileTreeInternal fileTree) {
        // TODO: If we could get the backing file we could add it as root element
        return new DefaultFileSnapshotTree(null, fileSystemSnapshotter.snapshotTree(fileTree));
    }

    public DefaultFileSnapshotTree directoryTree(DirectoryFileTree directoryFileTree) {
        FileTreeSnapshot root = fileSystemSnapshotter.snapshotDirectoryTree(directoryFileTree.getDir());
        return new DefaultFileSnapshotTree(fileSystemSnapshotter.snapshotSelf(new File(root.getPath())), root.getDescendants());
    }

    public List<FileSnapshotTree> fileCollection(FileCollection input) {
        LinkedList<FileSnapshotTree> fileTreeElements = Lists.newLinkedList();
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl(fileTreeElements);
        fileCollection.visitRootElements(visitor);
        return fileTreeElements;
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor {
        private final List<FileSnapshotTree> fileTreeElements;

        FileCollectionVisitorImpl(List<FileSnapshotTree> fileTreeElements) {
            this.fileTreeElements = fileTreeElements;
        }

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                FileSnapshot details = fileSystemSnapshotter.snapshotSelf(file);
                switch (details.getType()) {
                    case Missing:
                        fileTreeElements.add(details);
                        break;
                    case RegularFile:
                        fileTreeElements.add(details);
                        break;
                    case Directory:
                        // Visit the directory itself, then its contents
                        fileTreeElements.add(details);
                        visitDirectoryTree(directoryFileTreeFactory.create(file));
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            fileTreeElements.add(fileTree(fileTree));
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryTree) {
            fileTreeElements.add(directoryTree(directoryTree));
        }
    }
}
