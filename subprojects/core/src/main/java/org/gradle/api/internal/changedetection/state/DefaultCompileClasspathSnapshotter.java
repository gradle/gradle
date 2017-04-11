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

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultCompileClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    private static final Comparator<FileSnapshot> FILE_DETAILS_COMPARATOR = new Comparator<FileSnapshot>() {
        @Override
        public int compare(FileSnapshot o1, FileSnapshot o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };
    private final StringInterner stringInterner;
    private final ClasspathEntryHasher classpathEntryHasher;

    public DefaultCompileClasspathSnapshotter(FileSnapshotTreeFactory fileSnapshotTreeFactory, StringInterner stringInterner, ClasspathEntryHasher classpathEntryHasher) {
        super(fileSnapshotTreeFactory, stringInterner);
        this.stringInterner = stringInterner;
        this.classpathEntryHasher = classpathEntryHasher;
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        return super.snapshot(input, TaskFilePropertyCompareStrategy.ORDERED, snapshotNormalizationStrategy);
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }

    @Override
    protected FileCollectionSnapshotCollector createCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotCollector(ClasspathSnapshotNormalizationStrategy.INSTANCE, TaskFilePropertyCompareStrategy.ORDERED);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new CompileClasspathResourceSnapshotter(ClasspathSnapshotNormalizationStrategy.INSTANCE, TaskFilePropertyCompareStrategy.ORDERED);
    }

    private List<FileSnapshot> normaliseTreeElements(List<FileSnapshot> fileDetails) {
        // TODO: We could rework this to produce a FileSnapshot for the directory that
        // has a hash for the contents of this directory vs returning a list of the contents
        // of the directory with their hashes
        // Collect the signatures of each class file
        List<FileSnapshot> sorted = new ArrayList<FileSnapshot>(fileDetails.size());
        for (FileSnapshot details : fileDetails) {
            if (details.getType() == FileType.RegularFile) {
                HashCode signatureForClass = classpathEntryHasher.hash(details);
                if (signatureForClass == null) {
                    // Should be excluded
                    continue;
                }
                sorted.add(details.withContentHash(signatureForClass));
            }
        }

        // Sort as their order is not important
        Collections.sort(sorted, FILE_DETAILS_COMPARATOR);
        return sorted;
    }

    private class CompileClasspathResourceSnapshotter extends AbstractResourceSnapshotter {
        public CompileClasspathResourceSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
            super(normalizationStrategy, compareStrategy, stringInterner);
        }

        @Override
        public void snapshot(FileSnapshotTree fileTreeSnapshot) {
            FileSnapshot root = fileTreeSnapshot.getRoot();
            if (root != null) {
                if (root.getType() == FileType.RegularFile && FileUtils.isJar(root.getName())) {
                    HashCode signature = classpathEntryHasher.hash(root);
                    if (signature != null) {
                        recordSnapshot(root.withContentHash(signature));
                    }
                }
            }
            Iterable<? extends FileSnapshot> elements = fileTreeSnapshot.getElements();
            List<FileSnapshot> normalisedElements = normaliseTreeElements(Lists.newArrayList(elements));
            for (FileSnapshot element : normalisedElements) {
                recordSnapshot(element);
            }
        }
    }
}
