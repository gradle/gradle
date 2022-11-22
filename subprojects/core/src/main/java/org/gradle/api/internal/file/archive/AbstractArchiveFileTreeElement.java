/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.base.Preconditions;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.file.Chmod;
import org.gradle.util.internal.GFileUtils;

import java.io.File;

/**
 * An implementation of {@link org.gradle.api.file.FileTreeElement FileTreeElement} meant
 * for use with archive files when subclassing {@link org.gradle.api.internal.file.AbstractFileTree AbstractFileTree}.
 * <p>
 * This implementation extracts the files from the archive to the supplied cache directory.
 */
public abstract class AbstractArchiveFileTreeElement extends AbstractFileTreeElement {
    private final PersistentCache expansionCache;
    private final File expandedDir;
    private File file;

    /**
     * Creates a new instance.
     *
     * @param expansionCache the cache to use for extracting the archive
     * @param expandedDir the directory to extract the archive to (must be a subdirectory of the base cache directory)
     * @param chmod the chmod instance to use
     */
    protected AbstractArchiveFileTreeElement(Chmod chmod, PersistentCache expansionCache, File expandedDir) {
        super(chmod);

        Preconditions.checkArgument(expandedDir.getParentFile().equals(expansionCache.getBaseDir()), "Expanded dir must be located in the given cache");
        this.expansionCache = expansionCache;
        this.expandedDir = expandedDir;
    }

    /**
     * Returns a safe name for the name of a file contained in the archive.
     * @see org.gradle.util.internal.ZipSlip#safeZipEntryName(String)
     */
    protected abstract String safeEntryName();

    private File expandToCache(String entryName) {
        return expansionCache.useCache(() -> {
            File file = new File(expandedDir, entryName);
            if (!file.exists()) {
                GFileUtils.mkdirs(file.getParentFile());
                copyTo(file);
            }
            return file;
        });
    }

    @Override
    public File getFile() {
        if (file == null) {
            file = expandToCache(safeEntryName());
        }
        return file;
    }
}
