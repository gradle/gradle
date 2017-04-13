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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.ClasspathResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshotCollector;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.internal.nativeintegration.filesystem.FileType;

public class DefaultCompileClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements CompileClasspathSnapshotter {
    private final StringInterner stringInterner;
    private final ClasspathEntryHasher classpathEntryHasher;

    public DefaultCompileClasspathSnapshotter(FileSnapshotTreeFactory fileSnapshotTreeFactory, StringInterner stringInterner, ClasspathEntryHasher classpathEntryHasher) {
        super(fileSnapshotTreeFactory, stringInterner);
        this.stringInterner = stringInterner;
        this.classpathEntryHasher = classpathEntryHasher;
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return CompileClasspathSnapshotter.class;
    }

    @Override
    protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotBuilder(TaskFilePropertySnapshotNormalizationStrategy.NONE, TaskFilePropertyCompareStrategy.ORDERED, stringInterner);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new ClasspathResourceSnapshotter(new CompileClasspathEntrySnapshotter(classpathEntryHasher), stringInterner);
    }

    public static class CompileClasspathEntrySnapshotter implements ResourceSnapshotter {
        private final ClasspathEntryHasher classpathEntryHasher;

        public CompileClasspathEntrySnapshotter(ClasspathEntryHasher classpathEntryHasher) {
            this.classpathEntryHasher = classpathEntryHasher;
        }

        @Override
        public void snapshot(SnapshotTree details, SnapshotCollector collector) {
            SnapshottableResource root = details.getRoot();
            if (root != null && root.getType() == FileType.RegularFile && root.getName().endsWith(".class")) {
                HashCode signatureForClass = classpathEntryHasher.hash(root);
                if (signatureForClass != null) {
                    collector.recordSnapshot(root, signatureForClass);
                }
            }
        }
    }
}
