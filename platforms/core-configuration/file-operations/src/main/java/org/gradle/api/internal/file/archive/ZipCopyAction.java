/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.Zip64RequiredException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.internal.file.copy.ZipCompressor;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.IoActions;

import javax.annotation.Nullable;
import java.io.File;

public class ZipCopyAction implements CopyAction {

    /**
     * This field is internal API and should not be used in build scripts.
     * It is deprecated in order to allow graceful transition for potential users.
     *
     * @see ZipEntryConstants#CONSTANT_TIME_FOR_ZIP_ENTRIES
     */
    @Deprecated
    public static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES;

    private final File zipFile;
    private final ZipCompressor compressor;
    private final DocumentationRegistry documentationRegistry;
    private final String encoding;
    private final boolean preserveFileTimestamps;

    public ZipCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry, @Nullable String encoding, boolean preserveFileTimestamps) {
        this.zipFile = zipFile;
        this.compressor = compressor;
        this.documentationRegistry = documentationRegistry;
        this.encoding = encoding;
        this.preserveFileTimestamps = preserveFileTimestamps;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        final ZipArchiveOutputStream zipOutStr;

        try {
            zipOutStr = compressor.createArchiveOutputStream(zipFile);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create ZIP '%s'.", zipFile), e);
        }

        try {
            IoActions.withResource(zipOutStr, outputStream -> {
                stream.process(new StreamAction(outputStream, encoding));
            });
        } catch (Exception e) {
            if (e.getCause() instanceof Zip64RequiredException) {
                throw new org.gradle.api.tasks.bundling.internal.Zip64RequiredException(
                    String.format("%s\n\nTo build this archive, please enable the zip64 extension.\nSee: %s",
                        e.getCause().getMessage(), documentationRegistry.getDslRefForProperty("org.gradle.api.tasks.bundling.Zip", "zip64"))
                );
            }
            zipFile.delete();
            throw e;
        }

        return WorkResults.didWork(true);
    }

    private class StreamAction implements CopyActionProcessingStreamAction {
        private final ZipArchiveOutputStream zipOutStr;

        public StreamAction(ZipArchiveOutputStream zipOutStr, @Nullable String encoding) {
            this.zipOutStr = zipOutStr;
            if (encoding != null) {
                this.zipOutStr.setEncoding(encoding);
            }
        }

        @Override
        public void processFile(FileCopyDetailsInternal details) {
            if (details.isDirectory()) {
                visitDir(details);
            } else {
                visitFile(details);
            }
        }

        private void visitFile(FileCopyDetails fileDetails) {
            try {
                ZipArchiveEntry archiveEntry = new ZipArchiveEntry(fileDetails.getRelativePath().getPathString());
                archiveEntry.setTime(getArchiveTimeFor(fileDetails));
                archiveEntry.setUnixMode(UnixStat.FILE_FLAG | fileDetails.getPermissions().toUnixNumeric());
                zipOutStr.putArchiveEntry(archiveEntry);
                fileDetails.copyTo(zipOutStr);
                zipOutStr.closeArchiveEntry();
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e);
            }
        }

        private void visitDir(FileCopyDetails dirDetails) {
            try {
                // Trailing slash in name indicates that entry is a directory
                ZipArchiveEntry archiveEntry = new ZipArchiveEntry(dirDetails.getRelativePath().getPathString() + '/');
                archiveEntry.setTime(getArchiveTimeFor(dirDetails));
                archiveEntry.setUnixMode(UnixStat.DIR_FLAG | dirDetails.getPermissions().toUnixNumeric());
                zipOutStr.putArchiveEntry(archiveEntry);
                zipOutStr.closeArchiveEntry();
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e);
            }
        }
    }

    private long getArchiveTimeFor(FileCopyDetails details) {
        return preserveFileTimestamps ? details.getLastModified() : ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES;
    }
}
