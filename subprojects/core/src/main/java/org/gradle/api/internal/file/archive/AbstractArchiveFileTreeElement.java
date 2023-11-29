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
import org.gradle.api.file.SymbolicLinkDetails;
import org.gradle.api.internal.file.CopyableFileTreeElement;
import org.gradle.api.internal.file.DefaultFilePermissions;

import javax.annotation.Nullable;
import java.io.File;

/**
 * An implementation of {@link org.gradle.api.file.FileTreeElement FileTreeElement} meant
 * for use with archive files when subclassing {@link org.gradle.api.internal.file.AbstractFileTree AbstractFileTree}.
 * <p>
 * This implementation extracts the files from the archive to the supplied expansion directory.
 */
public abstract class AbstractArchiveFileTreeElement<ENTRY, METADATA extends ArchiveVisitor<ENTRY>> extends CopyableFileTreeElement implements FileVisitDetails {

    protected final METADATA archiveMetadata;
    protected final ENTRY entry;
    protected final ENTRY resultEntry;
    protected final ArchiveSymbolicLinkDetails<ENTRY> linkDetails;
    protected final boolean preserveLink;
    private final RelativePath relativePath;
    private File file;

    protected AbstractArchiveFileTreeElement(
        METADATA archiveMetadata,
        ENTRY entry,
        String targetPath,
        @Nullable ArchiveSymbolicLinkDetails<ENTRY> linkDetails,
        boolean preserveLink
    ) {
        super(archiveMetadata.chmod);
        this.entry = entry;
        this.archiveMetadata = archiveMetadata;
        this.linkDetails = linkDetails;
        this.preserveLink = preserveLink;
        this.resultEntry = getResultEntry();
        this.relativePath = new RelativePath(!archiveMetadata.isDirectory(resultEntry), targetPath.split("/"));
    }

    protected ENTRY getResultEntry() {
        if (archiveMetadata.isSymlink(entry) && !preserveLink && linkDetails.targetExists()) {
            return linkDetails.getTargetEntry();
        } else {
            return entry;
        }
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
        return relativePath;
    }

    @Override
    public boolean isSymbolicLink() {
        return preserveLink && archiveMetadata.isSymlink(entry);
    }

    @Override
    public boolean isDirectory() {
        return !relativePath.isFile();
    }

    @Override
    public FilePermissions getPermissions() {
        int unixMode = archiveMetadata.getUnixMode(resultEntry);
        if (unixMode != 0) {
            return new DefaultFilePermissions(unixMode);
        }

        return super.getPermissions();
    }

    @Override
    public long getLastModified() {
        return archiveMetadata.getLastModifiedTime(resultEntry);
    }

    @Override
    public long getSize() {
        return archiveMetadata.getSize(resultEntry);
    }

    @Nullable
    @Override
    public SymbolicLinkDetails getSymbolicLinkDetails() {
        return linkDetails;
    }

    @Override
    public void stopVisiting() {
        archiveMetadata.stopFlag.set(true);
    }

}
