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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.HashCode;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.List;

public class DefaultCompileClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    private final ClasspathContentHasher classpathContentHasher;

    public DefaultCompileClasspathSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter, ClasspathContentHasher classpathContentHasher) {
        super(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
        this.classpathContentHasher = classpathContentHasher;
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection files, TaskFilePropertyCompareStrategy compareStrategy, SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        return super.snapshot(
            files,
            new CompileClasspathResourceCollectionSnapshotBuilder(classpathContentHasher, getStringInterner()));
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }

    private class CompileClasspathResourceCollectionSnapshotBuilder extends ResourceCollectionSnapshotBuilder {
        private final ClasspathContentHasher classpathContentHasher;

        public CompileClasspathResourceCollectionSnapshotBuilder(ClasspathContentHasher classpathContentHasher, StringInterner stringInterner) {
            super(TaskFilePropertyCompareStrategy.ORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE, stringInterner);
            this.classpathContentHasher = classpathContentHasher;
        }

        @Override
        public void visitResourceTree(List<FileSnapshot> descendants) {
            ClasspathEntryResourceCollectionBuilder entryResourceCollectionBuilder = new ClasspathEntryResourceCollectionBuilder(getStringInterner());
            for (FileSnapshot descendant : descendants) {
                if (descendant.getType() == FileType.RegularFile) {
                    RegularFileSnapshot fileSnapshot = (RegularFileSnapshot) descendant;
                    entryResourceCollectionBuilder.visitFile(fileSnapshot, classpathContentHasher.getHash(fileSnapshot));
                }
            }
            entryResourceCollectionBuilder.collectNormalizedSnapshots(this);
        }

        @Override
        public void visitFile(RegularFileSnapshot file) {
            if (FileUtils.isJar(file.getName())) {
                HashCode hash = new ZipResourceTree(file, classpathContentHasher, getStringInterner()).getHash();
                if (hash != null) {
                    super.visitFile(file.withContentHash(hash));
                }
            }
        }
    }
}
