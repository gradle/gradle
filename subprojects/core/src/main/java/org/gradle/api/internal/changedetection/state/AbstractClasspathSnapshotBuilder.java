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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.List;

public abstract class AbstractClasspathSnapshotBuilder extends FileCollectionSnapshotBuilder {
    private final ContentHasher classpathContentHasher;
    private final ContentHasher jarContentHasher;
    protected final StringInterner stringInterner;

    public AbstractClasspathSnapshotBuilder(ContentHasher classpathContentHasher, ContentHasher jarContentHasher, StringInterner stringInterner) {
        super(TaskFilePropertyCompareStrategy.ORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE, stringInterner);
        this.stringInterner = stringInterner;
        this.jarContentHasher = jarContentHasher;
        this.classpathContentHasher = classpathContentHasher;
    }

    @Override
    public void visitFileTreeSnapshot(List<FileSnapshot> descendants) {
        ClasspathEntrySnapshotBuilder entryResourceCollectionBuilder = new ClasspathEntrySnapshotBuilder(stringInterner);
        for (FileSnapshot descendant : descendants) {
            if (descendant.getType() == FileType.RegularFile) {
                RegularFileSnapshot fileSnapshot = (RegularFileSnapshot) descendant;
                entryResourceCollectionBuilder.visitFile(fileSnapshot, classpathContentHasher.hash(fileSnapshot));
            }
        }
        entryResourceCollectionBuilder.collectNormalizedSnapshots(this);
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        if (FileUtils.isJar(file.getName())) {
            visitJar(file);
        } else {
            visitNonJar(file);
        }
    }

    protected abstract void visitNonJar(RegularFileSnapshot file);

    private void visitJar(RegularFileSnapshot file) {
        HashCode hash = jarContentHasher.hash(file);
        if (hash != null) {
            collectFileSnapshot(file.withContentHash(hash));
        }
    }
}
