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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.internal.file.Chmod;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.PathTraversalChecker;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An implementation of {@link org.gradle.api.file.FileTreeElement FileTreeElement} meant
 * for use with archive files when subclassing {@link org.gradle.api.internal.file.AbstractFileTree AbstractFileTree}.
 * <p>
 * This implementation extracts the files from the archive to the supplied expansion directory.
 */
public abstract class AbstractArchiveFileTreeElement extends AbstractFileTreeElement implements FileVisitDetails {
    private final File expandedDir;
    private File file;
    private final AtomicBoolean stopFlag;

    /**
     * Creates a new instance.
     *
     * @param chmod the chmod instance to use
     * @param expandedDir the directory to extract the archived file to
     * @param stopFlag the stop flag to use
     */
    protected AbstractArchiveFileTreeElement(Chmod chmod, File expandedDir, AtomicBoolean stopFlag) {
        super(chmod);
        this.expandedDir = expandedDir;
        this.stopFlag = stopFlag;
    }

    /**
     * Returns the archive entry for this element.
     *
     * @return the archive entry
     * @implSpec this method should be overriden to return a more specific type
     */
    protected abstract ArchiveEntry getArchiveEntry();

    /**
     * Returns a safe name for the name of a file contained in the archive.
     *
     * @see org.gradle.util.internal.PathTraversalChecker#safePathName(String)
     */
    protected String safeEntryName() {
        return PathTraversalChecker.safePathName(getEntryName());
    }

    /**
     * Returns unsafe name for the name of a file contained in the archive.
     *
     * @see AbstractArchiveFileTreeElement#safeEntryName
     */
    protected abstract String getEntryName();

    @Override
    public File getFile() {
        if (file == null) {
            file = new File(expandedDir, safeEntryName());
            if (!file.exists()) {
                GFileUtils.mkdirs(file.getParentFile());
                copyTo(file);
            }
        }
        return file;
    }

    @Override
    public RelativePath getRelativePath() {
        return new RelativePath(!getArchiveEntry().isDirectory(), safeEntryName().split("/"));
    }

    @Override
    public long getLastModified() {
        return getArchiveEntry().getLastModifiedDate().getTime();
    }

    @Override
    public boolean isDirectory() {
        return getArchiveEntry().isDirectory();
    }

    @Override
    public long getSize() {
        return getArchiveEntry().getSize();
    }

    @Override
    public void stopVisiting() {
        stopFlag.set(true);
    }
}
