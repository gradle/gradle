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

import org.apache.tools.zip.UnixStat;
import org.apache.tools.zip.Zip64RequiredException;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.internal.file.copy.ZipCompressor;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.IoActions;
import org.gradle.util.GUtil;

import java.io.File;

public class ZipCopyAction implements CopyAction {
    private final File zipFile;
    private final ZipCompressor compressor;
    private final DocumentationRegistry documentationRegistry;
    private final String encoding;
    private final boolean preserveFileTimestamps;

    public ZipCopyAction(File zipFile, ZipCompressor compressor, DocumentationRegistry documentationRegistry, String encoding, boolean preserveFileTimestamps) {
        this.zipFile = zipFile;
        this.compressor = compressor;
        this.documentationRegistry = documentationRegistry;
        this.encoding = encoding;
        this.preserveFileTimestamps = preserveFileTimestamps;
    }

    public WorkResult execute(final CopyActionProcessingStream stream) {
        final ZipOutputStream zipOutStr;

        try {
            zipOutStr = compressor.createArchiveOutputStream(zipFile);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create ZIP '%s'.", zipFile), e);
        }

        try {
            IoActions.withResource(zipOutStr, new Action<ZipOutputStream>() {
                public void execute(ZipOutputStream outputStream) {
                    stream.process(new StreamAction(outputStream, encoding));
                }
            });
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof Zip64RequiredException) {
                throw new org.gradle.api.tasks.bundling.internal.Zip64RequiredException(
                        String.format("%s\n\nTo build this archive, please enable the zip64 extension.\nSee: %s", e.getCause().getMessage(), documentationRegistry.getDslRefForProperty(Zip.class, "zip64"))
                );
            }
        }

        return new SimpleWorkResult(true);
    }

    private class StreamAction implements CopyActionProcessingStreamAction {
        private final ZipOutputStream zipOutStr;

        public StreamAction(ZipOutputStream zipOutStr, String encoding) {
            this.zipOutStr = zipOutStr;
            if (encoding != null) {
                this.zipOutStr.setEncoding(encoding);
            }
        }

        public void processFile(FileCopyDetailsInternal details) {
            if (details.isDirectory()) {
                visitDir(details);
            } else {
                visitFile(details);
            }
        }

        private void visitFile(FileCopyDetails fileDetails) {
            try {
                ZipEntry archiveEntry = new ZipEntry(fileDetails.getRelativePath().getPathString());
                archiveEntry.setTime(getArchiveTimeFor(fileDetails));
                archiveEntry.setUnixMode(UnixStat.FILE_FLAG | fileDetails.getMode());
                zipOutStr.putNextEntry(archiveEntry);
                fileDetails.copyTo(zipOutStr);
                zipOutStr.closeEntry();
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e);
            }
        }

        private void visitDir(FileCopyDetails dirDetails) {
            try {
                // Trailing slash in name indicates that entry is a directory
                ZipEntry archiveEntry = new ZipEntry(dirDetails.getRelativePath().getPathString() + '/');
                archiveEntry.setTime(getArchiveTimeFor(dirDetails));
                archiveEntry.setUnixMode(UnixStat.DIR_FLAG | dirDetails.getMode());
                zipOutStr.putNextEntry(archiveEntry);
                zipOutStr.closeEntry();
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e);
            }
        }
    }

    private long getArchiveTimeFor(FileCopyDetails details) {
        return preserveFileTimestamps ? details.getLastModified() : GUtil.CONSTANT_TIME_FOR_ZIP_ENTRIES;
    }
}
