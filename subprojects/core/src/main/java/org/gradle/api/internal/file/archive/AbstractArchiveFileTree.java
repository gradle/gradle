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
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.scopes.ScopedCache;

import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

/**
 * Abstract base class for a {@link org.gradle.api.file.FileTree FileTree} that is backed by an archive file.
 *
 * Will decompress the archive file to a managed cache directory, so that access to the archive's contents
 * are only permitted one at a time.  The cache directory is a Gradle cross version cache.
 */
/* package */ abstract class AbstractArchiveFileTree implements FileSystemMirroringFileTree, TaskDependencyContainer {
    private static final String EXPANSION_CACHE_KEY = "compressed-file-expansion";
    private static final String EXPANSION_CACHE_NAME = "Compressed Files Expansion Cache";

    protected final PersistentCache expansionCache;

    protected AbstractArchiveFileTree(ScopedCache decompressionCacheFactory) {
        this.expansionCache = decompressionCacheFactory.cache(EXPANSION_CACHE_KEY)
                .withDisplayName(EXPANSION_CACHE_NAME)
                .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();
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
