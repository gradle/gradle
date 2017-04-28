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
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.specs.Spec;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileType;

import java.util.List;
import java.util.Set;

public abstract class AbstractClasspathSnapshotBuilder extends FileCollectionSnapshotBuilder {
    private final ContentHasher classpathContentHasher;
    private final Set<Spec<RelativePath>> ignoreSpecs;
    private final ContentHasher jarContentHasher;
    protected final StringInterner stringInterner;

    public AbstractClasspathSnapshotBuilder(ContentHasher classpathContentHasher, ContentHasher jarContentHasher, StringInterner stringInterner, Set<Spec<RelativePath>> ignoreSpecs) {
        super(TaskFilePropertyCompareStrategy.ORDERED, TaskFilePropertySnapshotNormalizationStrategy.NONE, stringInterner);
        this.stringInterner = stringInterner;
        this.jarContentHasher = jarContentHasher;
        this.classpathContentHasher = classpathContentHasher;
        this.ignoreSpecs = ignoreSpecs;
    }

    @Override
    public void visitFileTreeSnapshot(List<FileSnapshot> descendants) {
        ClasspathEntrySnapshotBuilder entryResourceCollectionBuilder = new ClasspathEntrySnapshotBuilder(stringInterner);
        for (FileSnapshot descendant : descendants) {
            if (descendant.getType() == FileType.RegularFile) {
                RegularFileSnapshot fileSnapshot = (RegularFileSnapshot) descendant;
                if (shouldIgnore(fileSnapshot)) {
                    continue;
                }
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

    private boolean shouldIgnore(FileSnapshot fileSnapshot) {
        if (ignoreSpecs.isEmpty()) {
            return false;
        }
        for (Spec<RelativePath> ignoreSpec : ignoreSpecs) {
            if (ignoreSpec.isSatisfiedBy(fileSnapshot.getRelativePath())) {
                return true;
            }
        }
        return false;
    }
}
