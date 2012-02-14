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

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.apache.tools.zip.UnixStat;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.EmptyCopySpecVisitor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class TarCopySpecVisitor extends EmptyCopySpecVisitor {
    private TarOutputStream tarOutStr;
    private File tarFile;

    public void startVisit(CopyAction action) {
        TarCopyAction archiveAction = (TarCopyAction) action;
        try {
            tarFile = archiveAction.getArchivePath();
            OutputStream outStr = archiveAction.getCompressor().compress(tarFile);
            tarOutStr = new TarOutputStream(outStr);
            tarOutStr.setLongFileMode(TarOutputStream.LONGFILE_GNU);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create TAR '%s'.", tarFile), e);
        }
    }

    public void endVisit() {
        try {
            tarOutStr.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            tarOutStr = null;
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
            final TarEntry entry = new TarEntry(getEntryPath(fileDetails));
            entry.setModTime(fileDetails.getLastModified());
            entry.setMode(getEntryMode(fileDetails));
            writeEntry(fileDetails, entry);
            tarOutStr.closeEntry();
        } catch (Exception e) {
            throw new GradleException(String.format("Could not add %s to TAR '%s'.", fileDetails, tarFile), e);
        }
    }

    private String getEntryPath(FileVisitDetails fileDetails) {
        // Trailing slash on name indicates entry is a directory
        return fileDetails.getRelativePath().getPathString()
            + (fileDetails.isDirectory() ? '/' : "");
    }

    private int getEntryMode(FileVisitDetails fileDetails) {
        return (fileDetails.isDirectory() ? UnixStat.DIR_FLAG : UnixStat.FILE_FLAG)
            | fileDetails.getMode();
    }

    private void writeEntry(FileVisitDetails fileDetails, TarEntry entry) throws IOException {
        if (!fileDetails.isDirectory()) {
            entry.setSize(fileDetails.getSize());
        }
        tarOutStr.putNextEntry(entry);
        if (!fileDetails.isDirectory()) {
            fileDetails.copyTo(tarOutStr);
        }
    }
}
