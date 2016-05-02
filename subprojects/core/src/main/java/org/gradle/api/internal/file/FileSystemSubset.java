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
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.internal.file.collections.DirectoryTrees;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.services.FileSystems;

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

    public FileSystemSubset(ImmutableCollection<File> files, ImmutableCollection<ImmutableDirectoryTree> trees) {
        this.files = files;
        this.trees = trees;
    }

    public Iterable<? extends File> getRoots() {
        return FileUtils.calculateRoots(
            Iterables.concat(files, Iterables.transform(trees, new Function<DirectoryTree, File>() {
                @Override
                public File apply(DirectoryTree input) {
                    return input.getDir();
                }
            }))
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
        String pathWithSeparator = file.getAbsolutePath() + File.separator;
        for (File candidateFile : files) {
            String candidateFilePathWithSeparator = candidateFile.getPath() + File.separator;
            if (pathWithSeparator.startsWith(candidateFilePathWithSeparator)) {
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
    public static class Builder {
        private final ImmutableSet.Builder<File> files = ImmutableSet.builder();
        private final ImmutableSet.Builder<ImmutableDirectoryTree> trees = ImmutableSet.builder();
        private final Lock lock = new ReentrantLock();

        private Builder() {
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
