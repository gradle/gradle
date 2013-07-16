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
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.copy.EmptyCopySpecContentVisitor;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.internal.file.copy.ZipCompressor;

import java.io.File;
import java.io.IOException;

public class ZipCopySpecContentVisitor extends EmptyCopySpecContentVisitor {
    private ZipOutputStream zipOutStr;
    private final File zipFile;
    private final ZipCompressor compressor;

    public ZipCopySpecContentVisitor(File zipFile, ZipCompressor compressor) {
        this.zipFile = zipFile;
        this.compressor = compressor;
    }

    public void startVisit() {
        try {
            zipOutStr = compressor.createArchiveOutputStream(zipFile);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create ZIP '%s'.", zipFile), e);
        }
    }

    public void endVisit() {
        try {
            zipOutStr.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            zipOutStr = null;
        }
    }

    @Override
    public void visit(FileCopyDetailsInternal details) {
        if (details.isDirectory()) {
            visitDir(details);
        } else {
            visitFile(details);
        }
    }

    private void visitFile(FileCopyDetails fileDetails) {
        try {
            ZipEntry archiveEntry = new ZipEntry(fileDetails.getRelativePath().getPathString());
            archiveEntry.setTime(fileDetails.getLastModified());
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
            archiveEntry.setTime(dirDetails.getLastModified());
            archiveEntry.setUnixMode(UnixStat.DIR_FLAG | dirDetails.getMode());
            zipOutStr.putNextEntry(archiveEntry);
            zipOutStr.closeEntry();
        } catch (Exception e) {
            throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, zipFile), e);
        }
    }

    public boolean getDidWork() {
        return true;
    }
}
