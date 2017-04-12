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
import org.gradle.api.GradleException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.AbstractResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.resources.zip.ZipSnapshotTree;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.filesystem.FileType;

public class DefaultClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements ClasspathSnapshotter {
    private final StringInterner stringInterner;
    private final ClasspathEntryHasher classpathEntryHasher;

    public DefaultClasspathSnapshotter(FileSnapshotTreeFactory fileSnapshotTreeFactory, StringInterner stringInterner, ClasspathEntryHasher classpathEntryHasher) {
        super(fileSnapshotTreeFactory, stringInterner);
        this.stringInterner = stringInterner;
        this.classpathEntryHasher = classpathEntryHasher;
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return ClasspathSnapshotter.class;
    }

    @Override
    protected FileCollectionSnapshotCollector createCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotCollector(TaskFilePropertySnapshotNormalizationStrategy.NONE, TaskFilePropertyCompareStrategy.ORDERED);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new ClasspathResourceSnapshotter();
    }

    private class ClasspathResourceSnapshotter extends AbstractResourceSnapshotter {
        public ClasspathResourceSnapshotter() {
            super(TaskFilePropertySnapshotNormalizationStrategy.NONE, TaskFilePropertyCompareStrategy.ORDERED, stringInterner);
        }

        @Override
        public void snapshot(SnapshotTree fileTreeSnapshot) {
            SnapshottableResource root = fileTreeSnapshot.getRoot();
            if (root != null) {
                if (root.getType() == FileType.Missing) {
                    return;
                }
                SnapshotTree contents = (root.getType() == FileType.RegularFile) ? new ZipSnapshotTree(root) : fileTreeSnapshot;
                ClasspathEntrySnapshotter entrySnapshotter = null;
                try {
                    entrySnapshotter = new ClasspathEntrySnapshotter();
                    recordSnapshotter(root, entrySnapshotter);
                    for (SnapshottableResource resource : contents.getElements()) {
                        entrySnapshotter.snapshot(resource);
                    }
                } finally {
                    IoActions.closeQuietly(contents);
                }
            } else {
                throw new GradleException("Tree without root file on Classpath");
            }
        }
    }

    private class ClasspathEntrySnapshotter extends AbstractResourceSnapshotter {
        public ClasspathEntrySnapshotter() {
            super(TaskFilePropertySnapshotNormalizationStrategy.RELATIVE, TaskFilePropertyCompareStrategy.UNORDERED, stringInterner);
        }

        @Override
        public void snapshot(SnapshotTree details) {
            SnapshottableResource root = details.getRoot();
            if (root != null && root.getType() == FileType.RegularFile) {
                HashCode signatureForClass = root.getContent().getContentMd5();
                if (signatureForClass != null) {
                    recordSnapshot(root, signatureForClass);
                }
            }
        }
    }
}
