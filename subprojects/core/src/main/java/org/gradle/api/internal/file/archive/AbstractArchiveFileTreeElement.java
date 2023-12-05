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

import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.CopyableFileTreeElement;
import org.gradle.api.internal.file.DefaultFilePermissions;

import javax.annotation.Nullable;
import java.io.File;

import static org.gradle.internal.file.PathTraversalChecker.safePathName;

/**
 * An implementation of {@link org.gradle.api.file.FileTreeElement FileTreeElement} meant
 * for use with archive files when subclassing {@link org.gradle.api.internal.file.AbstractFileTree AbstractFileTree}.
 * <p>
 * This implementation can extract the files from the archive to the supplied expansion directory.
 */
public abstract class AbstractArchiveFileTreeElement<ENTRY, METADATA extends ArchiveVisitor<ENTRY>> extends CopyableFileTreeElement implements FileVisitDetails {

    protected final METADATA archiveMetadata;
    protected final ENTRY entry;
    protected ENTRY resultEntry = null;
    private final String targetPath;
    protected ArchiveSymbolicLinkDetails<ENTRY> linkDetails;
    private File file;
    private Boolean isLink = null;

    protected AbstractArchiveFileTreeElement(
        METADATA archiveMetadata,
        ENTRY entry,
        String targetPath
    ) {
        super(archiveMetadata.chmod);
        this.entry = entry;
        this.archiveMetadata = archiveMetadata;
        this.targetPath = targetPath;
    }

    protected ENTRY getResultEntry() {
        if (resultEntry == null) {
            if (getSymbolicLinkDetails() != null && getSymbolicLinkDetails().targetExists()) {
                resultEntry = linkDetails.getTargetEntry();
            } else {
                resultEntry = entry;
            }
        }
        return resultEntry;
    }

    @Override
    public File getFile() {
        if (file == null) {
            file = new File(archiveMetadata.expandedDir, archiveMetadata.getPath(entry));
            if (!file.exists()) {
                copyPreservingPermissions(file);
            }
        }
        return file;
    }

    @Override
    public RelativePath getRelativePath() {
        return new RelativePath(!isDirectory(), safePathName(targetPath).split("/"));
    }

    @Override
    public boolean isSymbolicLink() {
        return archiveMetadata.preserveLinks && isLink();
    }

    protected boolean isLink() {
        if (isLink == null) {
            // caching this for performance reasons
            isLink = archiveMetadata.isSymlink(entry);
        }
        return isLink;
    }

    @Override
    public boolean isDirectory() {
        return archiveMetadata.isDirectory(entry) || (!archiveMetadata.preserveLinks && isLink() && archiveMetadata.isDirectory(getResultEntry()));
    }

    @Override
    public FilePermissions getPermissions() {
        int unixMode = archiveMetadata.getUnixMode(getResultEntry());
        if (unixMode != 0) {
            return new DefaultFilePermissions(unixMode);
        }

        return super.getPermissions();
    }

    @Override
    public long getLastModified() {
        return archiveMetadata.getLastModifiedTime(getResultEntry());
    }

    @Override
    public long getSize() {
        return archiveMetadata.getSize(getResultEntry());
    }

    @Nullable
    @Override
    public ArchiveSymbolicLinkDetails<ENTRY> getSymbolicLinkDetails() {
        if (isLink() && linkDetails == null) {
            linkDetails = new ArchiveSymbolicLinkDetails<>(entry, archiveMetadata);
        }
        return linkDetails;
    }

    @Override
    public void stopVisiting() {
        archiveMetadata.stopFlag.set(true);
    }

}
