/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.internal.file.collections.DirectoryTrees;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.file.collections.GeneratedFiles;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.services.FileSystems;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a subset of the *potential* file system space.
 * <p>
 * No regard is given to what the actual file system represented by this actually looks like.
 * That is, no attention is paid to whether a particular file is a directory, regular file or anything else.
 * Instances do not access the filesystem.
 */
@ThreadSafe
public class FileSystemSubset {

    private final ImmutableCollection<File> files;
    private final ImmutableCollection<ImmutableDirectoryTree> trees;

    public static Builder builder() {
        return new Builder();
    }

    public static FileSystemSubset of(FileCollectionInternal fileCollection) {
        Builder subsetBuilder = builder();
        fileCollection.visitStructure(subsetBuilder);
        return subsetBuilder.build();
    }

    public FileSystemSubset(ImmutableCollection<File> files, ImmutableCollection<ImmutableDirectoryTree> trees) {
        this.files = files;
        this.trees = trees;
    }

    public Iterable<? extends File> getRoots() {
        return FileUtils.calculateRoots(
            Iterables.concat(files, Iterables.transform(trees, (Function<DirectoryTree, File>) input -> input.getDir()))
        );
    }

    public FileSystemSubset unfiltered() {
        return new FileSystemSubset(ImmutableList.copyOf(getRoots()), ImmutableList.<ImmutableDirectoryTree>of());
    }

    public boolean isEmpty() {
        return files.isEmpty() && trees.isEmpty();
    }

    public boolean contains(File file) {
        File absoluteFile = file.getAbsoluteFile();
        for (File candidateFile : files) {
            if (FileUtils.doesPathStartWith(file.getAbsolutePath(), candidateFile.getPath())) {
                return true;
            }
        }

        for (DirectoryTree tree : trees) {
            if (tree.getDir().getAbsoluteFile().equals(absoluteFile) || DirectoryTrees.contains(FileSystems.getDefault(), tree, absoluteFile)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Files: ").append(files);
        sb.append(" Trees: ").append(trees);
        return sb.toString();
    }

    @ThreadSafe
    public static class Builder implements FileCollectionStructureVisitor {
        private final ImmutableSet.Builder<File> files = ImmutableSet.builder();
        private final ImmutableSet.Builder<ImmutableDirectoryTree> trees = ImmutableSet.builder();
        private final Lock lock = new ReentrantLock();

        private Builder() {
        }

        @Override
        public VisitType prepareForVisit(FileCollectionInternal.Source source) {
            if (source instanceof GeneratedFiles) {
                // Don't watch generated resources
                return VisitType.NoContents;
            }
            // Only need the spec for other collections
            return VisitType.Spec;
        }

        @Override
        public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
            addFiles(contents);
        }

        @Override
        public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
            addFiles(fileTree);
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
            lock.lock();
            try {
                trees.add(ImmutableDirectoryTree.of(root, patterns));
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
            lock.lock();
            try {
                files.add(file.getAbsoluteFile());
            } finally {
                lock.unlock();
            }
        }

        private void addFiles(Iterable<File> contents) {
            lock.lock();
            try {
                for (File file : contents) {
                    files.add(file.getAbsoluteFile());
                }
            } finally {
                lock.unlock();
            }
        }

        public Builder add(FileSystemSubset fileSystemSubset) {
            lock.lock();
            try {
                files.addAll(fileSystemSubset.files);
                trees.addAll(fileSystemSubset.trees);
                return this;
            } finally {
                lock.unlock();
            }
        }

        public Builder add(File file) {
            lock.lock();
            try {
                files.add(file.getAbsoluteFile());
                return this;
            } finally {
                lock.unlock();
            }
        }

        public Builder add(DirectoryTree directoryTree) {
            lock.lock();
            try {
                trees.add(ImmutableDirectoryTree.of(directoryTree));
                return this;
            } finally {
                lock.unlock();
            }
        }

        public Builder add(File dir, PatternSet patternSet) {
            lock.lock();
            try {
                trees.add(ImmutableDirectoryTree.of(dir, patternSet));
                return this;
            } finally {
                lock.unlock();
            }
        }

        public FileSystemSubset build() {
            lock.lock();
            try {
                return new FileSystemSubset(files.build(), trees.build());
            } finally {
                lock.unlock();
            }
        }
    }

}
