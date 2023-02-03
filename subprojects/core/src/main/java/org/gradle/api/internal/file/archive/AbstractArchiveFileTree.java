/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.cache.internal.DecompressionCache;

import java.io.File;

/**
 * Abstract base class for a {@link org.gradle.api.file.FileTree FileTree} that is backed by an archive file.
 *
 * Will decompress the archive file to the given cache.
 */
/* package */ abstract class AbstractArchiveFileTree implements FileSystemMirroringFileTree, TaskDependencyContainer {
    protected final DecompressionCache decompressionCache;

    protected AbstractArchiveFileTree(DecompressionCache decompressionCache) {
        this.decompressionCache = decompressionCache;
    }

    abstract protected Provider<File> getBackingFileProvider();

    private File getBackingFile() {
        return getBackingFileProvider().get();
    }

    @Override
    public void visitStructure(MinimalFileTreeStructureVisitor visitor, FileTreeInternal owner) {
        File backingFile = getBackingFile();
        visitor.visitFileTreeBackedByFile(backingFile, owner, this);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(getBackingFileProvider());
    }
}
