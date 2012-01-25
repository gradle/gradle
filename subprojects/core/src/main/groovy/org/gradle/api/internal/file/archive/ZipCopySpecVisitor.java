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

import org.apache.tools.zip.*;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.copy.ArchiveCopyAction;
import org.gradle.api.internal.file.copy.EmptyCopySpecVisitor;

import java.io.File;
import java.io.IOException;

public class ZipCopySpecVisitor extends EmptyCopySpecVisitor {
    private ZipOutputStream zipOutStr;
    private File zipFile;

    public void startVisit(CopyAction action) {
        ArchiveCopyAction archiveAction = (ArchiveCopyAction) action;
        zipFile = archiveAction.getArchivePath();
        try {
            zipOutStr = new ZipOutputStream(zipFile);
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

    public void visitFile(FileVisitDetails fileDetails) {
        visitFileOrDir(fileDetails);
    }

    public void visitDir(FileVisitDetails dirDetails) {
        visitFileOrDir(dirDetails);
    }

    public boolean getDidWork() {
        return true;
    }

    private void visitFileOrDir(FileVisitDetails fileDetails) {
        try {
            final ZipEntry entry = new ZipEntry(getEntryPath(fileDetails));
            entry.setTime(fileDetails.getLastModified());
            entry.setUnixMode(getEntryMode(fileDetails));
            writeEntry(fileDetails, entry);
            zipOutStr.closeEntry();
        } catch (Exception e) {
            throw new GradleException(String.format("Could not add %s to ZIP '%s'.", fileDetails, zipFile), e);
        }
    }

    private String getEntryPath(FileVisitDetails fileDetails) {
        // Trailing slash in name indicates that entry is a directory
        return fileDetails.getRelativePath().getPathString()
            + (fileDetails.isDirectory() ? '/' : "");
    }

    private int getEntryMode(FileVisitDetails fileDetails) {
        return (fileDetails.isDirectory() ? UnixStat.DIR_FLAG : UnixStat.FILE_FLAG)
            | fileDetails.getMode();
    }

    private void writeEntry(FileVisitDetails fileDetails, ZipEntry entry) throws IOException {
        if (!fileDetails.isDirectory()) {
            entry.setMethod(ZipEntry.DEFLATED);
        }
        zipOutStr.putNextEntry(entry);
        if (!fileDetails.isDirectory()) {
            fileDetails.copyTo(zipOutStr);
        }
    }
}
