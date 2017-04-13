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
import org.gradle.api.internal.changedetection.resources.AbstractResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ClasspathResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.internal.Factory;
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
    protected FileCollectionSnapshotCollector createCollector(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new FileCollectionSnapshotCollector(TaskFilePropertySnapshotNormalizationStrategy.NONE, TaskFilePropertyCompareStrategy.ORDERED);
    }

    @Override
    protected ResourceSnapshotter createSnapshotter(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
        return new ClasspathResourceSnapshotter(new Factory<ResourceSnapshotter>() {
            @Override
            public ResourceSnapshotter create() {
                return new CompileClasspathEntrySnapshotter(classpathEntryHasher, stringInterner);
            }
        }, stringInterner);
    }

    public static class CompileClasspathEntrySnapshotter extends AbstractResourceSnapshotter {
        private final ClasspathEntryHasher classpathEntryHasher;

        public CompileClasspathEntrySnapshotter(ClasspathEntryHasher classpathEntryHasher, StringInterner stringInterner) {
            super(TaskFilePropertySnapshotNormalizationStrategy.RELATIVE, TaskFilePropertyCompareStrategy.UNORDERED, stringInterner);
            this.classpathEntryHasher = classpathEntryHasher;
        }

        @Override
        public void snapshot(SnapshotTree details) {
            SnapshottableResource root = details.getRoot();
            if (root != null && root.getType() == FileType.RegularFile && root.getName().endsWith(".class")) {
                HashCode signatureForClass = classpathEntryHasher.hash(root);
                if (signatureForClass != null) {
                    recordSnapshot(root, signatureForClass);
                }
            }
        }
    }
}
