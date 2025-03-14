/*
 * Copyright 2009 the original author or authors.
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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.archive.compression.ArchiveOutputStreamFactory;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;

import java.io.File;
import java.io.OutputStream;

public class TarCopyAction implements CopyAction {
    /**
     * An arbitrary timestamp chosen to provide constant file timestamps inside the tar archive.
     *
     * The value 0 is avoided to circumvent certain limitations of languages and applications that do not work well with the zero value.
     * (Like older Java implementations and libraries)
     *
     * The date is January 2, 1970.
     */
    public static final long CONSTANT_TIME_FOR_TAR_ENTRIES = 86400000;

    private final File tarFile;
    private final ArchiveOutputStreamFactory compressor;
    private final boolean preserveFileTimestamps;

    public TarCopyAction(File tarFile, ArchiveOutputStreamFactory compressor, boolean preserveFileTimestamps) {
        this.tarFile = tarFile;
        this.compressor = compressor;
        this.preserveFileTimestamps = preserveFileTimestamps;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {

        final OutputStream outStr;
        try {
            outStr = compressor.createArchiveOutputStream(tarFile);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create TAR '%s'.", tarFile), e);
        }

        try {
            IoActions.withResource(outStr, new ErroringAction<OutputStream>() {
                @Override
                protected void doExecute(final OutputStream outStr) throws Exception {
                    TarArchiveOutputStream tarOutStr;
                    try {
                        tarOutStr = new TarArchiveOutputStream(outStr);
                    } catch (Exception e) {
                        throw new GradleException(String.format("Could not create TAR '%s'.", tarFile), e);
                    }
                    tarOutStr.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    tarOutStr.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                    stream.process(new StreamAction(tarOutStr));
                    tarOutStr.close();
                }
            });
        } catch (Exception e) {
            tarFile.delete();
            throw e;
        }

        return WorkResults.didWork(true);
    }

    private class StreamAction implements CopyActionProcessingStreamAction {
        private final TarArchiveOutputStream tarOutStr;

        public StreamAction(TarArchiveOutputStream tarOutStr) {
            this.tarOutStr = tarOutStr;
        }

        @Override
        public void processFile(FileCopyDetailsInternal details) {
            try {
                boolean isDirectory = details.isDirectory();
                String entryName = details.getRelativePath().getPathString();
                if (isDirectory) {
                    // Trailing slash in name indicates that entry is a directory.
                    entryName += '/';
                }
                TarArchiveEntry archiveEntry = new TarArchiveEntry(entryName);
                archiveEntry.setModTime(getArchiveTimeFor(details));
                if (!isDirectory) {
                    archiveEntry.setSize(details.getSize());
                }
                int flag = isDirectory ? UnixStat.DIR_FLAG : UnixStat.FILE_FLAG;
                archiveEntry.setMode(flag | details.getPermissions().toUnixNumeric());
                tarOutStr.putArchiveEntry(archiveEntry);
                if (!isDirectory) {
                    details.copyTo(tarOutStr);
                }
                tarOutStr.closeArchiveEntry();
            } catch (Exception e) {
                throw new GradleException(String.format("Could not add %s to TAR '%s'.", details, tarFile), e);
            }
        }
    }

    private long getArchiveTimeFor(FileCopyDetails details) {
        return preserveFileTimestamps ? details.getLastModified() : CONSTANT_TIME_FOR_TAR_ENTRIES;
    }
}
